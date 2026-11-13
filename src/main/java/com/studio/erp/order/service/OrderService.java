package com.studio.erp.order.service;

import com.studio.erp.auth.CurrentUser;
import com.studio.erp.auth.CurrentUserHolder;
import com.studio.erp.common.audit.AuditService;
import com.studio.erp.common.enums.OrderStatus;
import com.studio.erp.common.enums.PaymentStatus;
import com.studio.erp.common.exception.BizException;
import com.studio.erp.common.exception.ErrorCode;
import com.studio.erp.employee.entity.Employee;
import com.studio.erp.employee.mapper.EmployeeMapper;
import com.studio.erp.order.dto.OrderCreateRequest;
import com.studio.erp.order.dto.OrderPaymentRequest;
import com.studio.erp.order.dto.OrderPricingRequest;
import com.studio.erp.order.entity.OrderItem;
import com.studio.erp.order.entity.OrderItemAssignee;
import com.studio.erp.order.entity.OrderStatusLog;
import com.studio.erp.order.entity.Payment;
import com.studio.erp.order.entity.PaymentAllocation;
import com.studio.erp.order.entity.SalesOrder;
import com.studio.erp.order.mapper.OrderItemAssigneeMapper;
import com.studio.erp.order.mapper.OrderItemMapper;
import com.studio.erp.order.mapper.OrderStatusLogMapper;
import com.studio.erp.order.mapper.PaymentAllocationMapper;
import com.studio.erp.order.mapper.PaymentMapper;
import com.studio.erp.order.mapper.SalesOrderMapper;
import com.studio.erp.performance.domain.ItemType;
import com.studio.erp.performance.domain.MoneyUtil;
import com.studio.erp.performance.domain.RatioUtil;
import com.studio.erp.performance.mapper.PerfQueryMapper;
import com.studio.erp.performance.service.PerformanceService;
import com.studio.erp.receivable.service.ReceivableService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 订单写操作：提单、定价、推进、登记回款。
 *
 * <p>【这个类是"钱的入口"，所以每一个方法都是事务】
 * 定价和回款都会牵动业绩流水。写完订单没写业绩，员工月底少钱；
 * 写完业绩没改订单状态，看板显示未收。任何一种半途而废都会让系统失去可信度，
 * 因此这里所有写方法都标注 {@code @Transactional(rollbackFor = Exception.class)}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    /** 金额零值，统一 scale=2，避免 0 与 0.00 在 equals 上不相等 */
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(MoneyUtil.SCALE, RoundingMode.HALF_UP);

    private static final DateTimeFormatter ORDER_NO_MONTH = DateTimeFormatter.ofPattern("yyMM");
    private static final DateTimeFormatter PAY_NO_MONTH = DateTimeFormatter.ofPattern("yyMM");

    private final SalesOrderMapper orderMapper;
    private final OrderItemMapper itemMapper;
    private final OrderItemAssigneeMapper assigneeMapper;
    private final PaymentMapper paymentMapper;
    private final PaymentAllocationMapper allocationMapper;
    private final EmployeeMapper employeeMapper;
    private final PerfQueryMapper perfQueryMapper;
    private final PerformanceService performanceService;
    private final OrderStatusLogMapper statusLogMapper;
    private final AuditService auditService;
    /**
     * 收款计划：定价时生成、交付时改尾款日、回款时重算。
     *
     * <p>【为什么由 OrderService 调用而不是发领域事件】
     * 这三件事必须和订单变更同事务。发事件就变成异步，
     * 异步失败时会出现"订单已定价但没有收款计划"，而催收页不会报错，
     * 只是那笔尾款从此没人催 —— 这种静默失败比报错危险得多。
     */
    private final ReceivableService receivableService;

    // =====================================================================
    // 1. 客服提单（无金额）
    // =====================================================================

    /**
     * 提单：只记"客户要做什么"，不记钱。
     *
     * <p>【为什么提完单不能直接生效】
     * 单价由主管录（需求明确），所以提单后必然有一段"待定价"的空窗。
     * 这段空窗里的订单不产生任何业绩，也不会出现在员工业绩里 ——
     * 如果这时候就算签单额，客服一天能开出十个"待定价"的单，业绩全是虚的。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long create(OrderCreateRequest request) {
        CurrentUser user = CurrentUserHolder.require();

        SalesOrder order = new SalesOrder();
        order.setOrderNo(nextOrderNo());
        order.setClientName(request.clientName().trim());
        order.setTitle(request.title().trim());
        order.setCategory(request.category());
        order.setCsEmpId(user.employeeId());
        order.setExpectDeliverDate(request.expectDeliverDate());
        order.setStatus(OrderStatus.PENDING_PRICE.getCode());
        order.setPaymentStatus(PaymentStatus.UNPAID.getCode());
        order.setTotalAmount(ZERO);
        order.setPerfBaseAmount(ZERO);
        order.setRemark(request.remark());
        order.setCreatedBy(user.employeeId());

        orderMapper.insert(order);
        // 首条流转记录：from 为空表示"订单产生"。时间线从这里开始，后面每一步都能接上
        logStatus(order.getId(), null, order.getStatus(), user.employeeId(), "提单");
        log.info("提单成功 orderNo={} client={} title={} 提单人={}",
                order.getOrderNo(), order.getClientName(), order.getTitle(), user.username());
        return order.getId();
    }

    // =====================================================================
    // 2. 主管定价
    // =====================================================================

    /**
     * 定价并确认订单（整单提交）。
     *
     * <p>【允许重新定价的条件】
     * 只有"一分钱都没收"的订单可以改价。一旦有了回款，CONFIRM 业绩流水就指向了
     * 具体的明细行 id，此时重做明细会让那些流水指向不存在的行，历史彻底断链。
     * 这种情况下正确的做法是废单重开（走退款冲销流程），而不是偷偷改数字。
     */
    @Transactional(rollbackFor = Exception.class)
    public void price(Long orderId, OrderPricingRequest request) {
        CurrentUser user = CurrentUserHolder.require();
        SalesOrder order = requireOrder(orderId);

        OrderStatus current = OrderStatus.of(order.getStatus());
        if (current != OrderStatus.PENDING_PRICE && current != OrderStatus.DRAFT) {
            throw new BizException(ErrorCode.ORDER_STATUS_INVALID,
                    "当前订单状态为【" + current.getLabel() + "】，只有待定价的订单可以录入价格");
        }
        // 已收款则不允许改价，原因见方法注释
        if (order.getPaymentStatus() != null && order.getPaymentStatus() != PaymentStatus.UNPAID.getCode()) {
            throw new BizException(ErrorCode.ORDER_PRICING_LOCKED);
        }

        List<ItemDraft> drafts = validateItems(request.items());
        checkAssignees(drafts);

        BigDecimal total = drafts.stream().map(ItemDraft::amount)
                .reduce(ZERO, BigDecimal::add);
        BigDecimal perfBase = drafts.stream()
                .filter(d -> d.type().isPerfEligible())
                .map(ItemDraft::amount)
                .reduce(ZERO, BigDecimal::add);

        LocalDate signDate = request.signDate() == null ? LocalDate.now() : request.signDate();

        // 先做状态 CAS：抢占失败说明有人先一步定过价了，此时不要动明细
        int updated = orderMapper.markPriced(orderId, total, perfBase, signDate, user.employeeId(),
                current.getCode(), OrderStatus.CONFIRMED.getCode(), request.remark());
        if (updated == 0) {
            throw new BizException(ErrorCode.CONFLICT, "订单状态已被他人修改，请刷新后重试");
        }

        // 明细整体重建：先删后插，避免"改了两行、剩下一行还是老价"的中间态
        assigneeMapper.deleteByOrder(orderId);
        itemMapper.deleteByOrder(orderId);

        int lineNo = 1;
        for (ItemDraft draft : drafts) {
            OrderItem item = new OrderItem();
            item.setOrderId(orderId);
            item.setLineNo(lineNo++);
            item.setItemName(draft.req().itemName().trim());
            item.setSpec(draft.req().spec());
            item.setUnit(draft.req().unit() == null || draft.req().unit().isBlank() ? "项" : draft.req().unit());
            item.setQty(draft.req().qty());
            item.setUnitPrice(MoneyUtil.yuan(draft.req().unitPrice()));
            item.setAmount(draft.amount());
            item.setItemType(draft.type().getCode());
            item.setStatus(1);
            item.setRemark(draft.req().remark());
            itemMapper.insert(item);

            if (draft.req().assignees() == null) {
                continue;
            }
            boolean first = true;
            for (OrderPricingRequest.Assignee a : draft.req().assignees()) {
                OrderItemAssignee entity = new OrderItemAssignee();
                entity.setOrderItemId(item.getId());
                entity.setEmployeeId(a.employeeId());
                // 主设/协作只影响展示顺序与署名，不影响金额计算
                entity.setAssigneeRole(first ? 1 : 2);
                entity.setRatioBp(a.ratioBp());
                assigneeMapper.insert(entity);
                first = false;
            }
        }

        log.info("定价完成 orderNo={} 总额={} 业绩基数={} 明细{}行 操作人={}",
                order.getOrderNo(), total.toPlainString(), perfBase.toPlainString(),
                drafts.size(), user.username());

        // 改价是最容易被质疑的动作（"这单明明报的是 8000"），金额前后值必须留痕。
        // 单据上的 total_amount 只会剩最后一次的值，历史只能靠这条记录还原。
        auditService.record("ORDER", orderId, "total_amount", order.getTotalAmount(), total);
        logStatus(orderId, current.getCode(), OrderStatus.CONFIRMED.getCode(),
                user.employeeId(), "定价确认");

        // 定价即产生"什么时候该收钱"：订金按签单日、尾款按交付月月末。
        // 放在事务内，避免"订单已确认但催收页看不到这张单"。
        receivableService.ensurePlans(orderId);
    }

    // =====================================================================
    // 3. 推进状态
    // =====================================================================

    /**
     * 推进订单执行状态。
     *
     * <p>【为什么取消订单要看有没有回款】
     * 已经收了钱的单子直接置为"已取消"，业绩还在、订单没了，月底对账必然少一块。
     * 正确路径是退款 → 生成冲销流水 → 再取消，所以这里直接拦住。
     */
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(Long orderId, int targetCode) {
        SalesOrder order = requireOrder(orderId);
        OrderStatus current = OrderStatus.of(order.getStatus());
        OrderStatus target = OrderStatus.of(targetCode);

        if (!current.canTransitTo(target)) {
            throw new BizException(ErrorCode.ORDER_STATUS_INVALID, String.format(
                    "订单当前为【%s】，不能直接变成【%s】", current.getLabel(), target.getLabel()));
        }
        if (target == OrderStatus.CANCELLED) {
            BigDecimal paid = perfQueryMapper.selectAllocatedAmount(orderId, null);
            if (paid != null && paid.signum() > 0) {
                throw new BizException(ErrorCode.ORDER_STATUS_INVALID,
                        "该订单已有回款 " + paid.toPlainString() + " 元，请先走退款冲销再取消");
            }
        }

        LocalDate deliverDate = target == OrderStatus.DELIVERED ? LocalDate.now() : null;
        int updated = orderMapper.markStatus(orderId, current.getCode(), target.getCode(), deliverDate);
        if (updated == 0) {
            throw new BizException(ErrorCode.CONFLICT, "订单状态已被他人修改，请刷新后重试");
        }
        log.info("订单状态推进 orderNo={} {} -> {}", order.getOrderNo(),
                current.getLabel(), target.getLabel());
        logStatus(orderId, current.getCode(), target.getCode(),
                CurrentUserHolder.require().employeeId(), null);

        // 交付意味着"可以收尾款了"：尾款应收日跟着挪到交付月的月末，
        // 否则 9 月签、11 月交付的单会被标成逾期 60 天，催收员会去催一张稿子还没交付的单
        if (target == OrderStatus.DELIVERED) {
            receivableService.onDelivered(orderId, deliverDate);
        }
    }

    /**
     * 写一条状态流转记录。
     *
     * <p>【为什么要独立成方法】状态在三处被改（提单、定价、推进），
     * 分散写容易漏一处，后面排查时时间线会断档。收敛到一个入口，漏不掉。
     */
    private void logStatus(Long orderId, Integer from, Integer to, Long operatorId, String remark) {
        OrderStatusLog row = new OrderStatusLog();
        row.setOrderId(orderId);
        row.setFromStatus(from);
        row.setToStatus(to);
        row.setOperatorId(operatorId);
        row.setRemark(remark);
        statusLogMapper.insert(row);
    }

    // =====================================================================
    // 4. 登记回款（业绩的触发点）
    // =====================================================================

    /**
     * 登记一笔回款并核销到本单，同时确认业绩。
     *
     * <p>【这个方法为什么必须在一个事务里】
     * 回款单、核销记录、业绩流水、订单收款状态，四件事同生共死。
     * 只成功一半的后果分别是：钱记了业绩没出（员工少钱）、
     * 业绩出了状态没改（看板显示未收）、核销没写（钱挂在库里没人认领）。
     * 每一种都要人工去数据库里修，而修账的人往往不敢动。
     *
     * <p>【业绩归属月 = 回款到账月】
     * 这是业务确认过的核心规则：本月未收到的尾款，下月收到就算下月的业绩。
     * 实现上就是把 payDate 传给业绩服务，由它取 YearMonth —— 不传"当前月"，
     * 因为财务经常是事后补录上周的到账，用当前月会算错月份。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long registerPayment(Long orderId, OrderPaymentRequest request) {
        CurrentUser user = CurrentUserHolder.require();
        SalesOrder order = requireOrder(orderId);

        OrderStatus status = OrderStatus.of(order.getStatus());
        if (status == OrderStatus.CANCELLED) {
            throw new BizException(ErrorCode.ORDER_STATUS_INVALID, "已取消的订单不能登记回款");
        }
        if (status == OrderStatus.DRAFT || status == OrderStatus.PENDING_PRICE) {
            throw new BizException(ErrorCode.ORDER_NOT_PRICED);
        }

        BigDecimal amount = MoneyUtil.yuan(request.amount());
        if (amount.signum() <= 0) {
            throw new BizException(ErrorCode.PAYMENT_AMOUNT_INVALID);
        }
        LocalDate payDate = request.payDate() == null ? LocalDate.now() : request.payDate();

        Payment payment = new Payment();
        payment.setPaymentNo(nextPaymentNo());
        payment.setClientName(order.getClientName());
        payment.setAmount(amount);
        payment.setPayDate(payDate);
        payment.setPayMethod(request.payMethod() == null ? 1 : request.payMethod());
        payment.setBankSerial(request.bankSerial());
        payment.setStatus(2);
        payment.setOperatorId(user.employeeId());
        payment.setRemark(request.remark());
        paymentMapper.insert(payment);

        PaymentAllocation allocation = new PaymentAllocation();
        allocation.setPaymentId(payment.getId());
        allocation.setOrderId(orderId);
        allocation.setAmount(amount);
        allocation.setCreatedBy(user.employeeId());
        allocationMapper.insert(allocation);

        // 核心：按核销记录生成业绩流水（内部校验是否超收，并靠唯一索引保证幂等）
        performanceService.confirmByAllocation(allocation.getId(), orderId, amount, payDate, user.employeeId());

        refreshPaymentStatus(orderId);
        // 回款要同步到收款计划：否则催收页还在催一笔昨天已经到账的尾款
        receivableService.recalc(orderId);

        log.info("登记回款 orderNo={} 金额={} 到账日={} 核销ID={} 操作人={}",
                order.getOrderNo(), amount.toPlainString(), payDate, allocation.getId(), user.username());
        return allocation.getId();
    }

    /**
     * 重算收款状态。
     *
     * <p>不用"上一次状态 + 本次金额"累加，而是每次重新汇总已核销总额。
     * 累加在并发或补录场景下会漂移，重算永远不会。
     */
    private void refreshPaymentStatus(Long orderId) {
        SalesOrder order = orderMapper.selectById(orderId);
        BigDecimal paid = perfQueryMapper.selectAllocatedAmount(orderId, null);
        PaymentStatus status = PaymentStatus.derive(paid, order.getTotalAmount());
        orderMapper.updatePaymentStatus(orderId, status.getCode());
    }

    // =====================================================================
    // 内部：校验与单号
    // =====================================================================

    /**
     * 校验并预计算每条明细的金额。
     *
     * <p>【金额在服务端算，绝不采信前端传的 amount】
     * 前端传金额意味着存在"传了个和 qty×unitPrice 对不上的数"的可能，
     * 而这种错误在界面上完全看不出来，只有月底对账才发现总额和明细加起来不等。
     */
    private List<ItemDraft> validateItems(List<OrderPricingRequest.Item> items) {
        List<ItemDraft> drafts = new ArrayList<>(items.size());
        for (int i = 0; i < items.size(); i++) {
            OrderPricingRequest.Item it = items.get(i);
            int no = i + 1;

            ItemType type;
            try {
                type = it.itemType() == null ? ItemType.NORMAL : ItemType.of(it.itemType());
            } catch (IllegalArgumentException e) {
                throw new BizException(ErrorCode.ORDER_ITEM_INVALID, "第 " + no + " 行的行类型不合法");
            }

            // 赠品与返工强制金额为 0：它们计单量但不计业绩。
            // 如果允许给赠品填个"原价"，那个价格会进总额，客户实际没付，就成了虚假签单额。
            BigDecimal amount = type.isPerfEligible()
                    ? MoneyUtil.multiply(it.qty(), it.unitPrice())
                    : ZERO;

            if (type.isPerfEligible() && amount.signum() <= 0) {
                throw new BizException(ErrorCode.ORDER_ITEM_INVALID,
                        "第 " + no + " 行【" + it.itemName() + "】金额为 0，请填写单价；若是赠品请把行类型改为赠品");
            }

            if (type.isPerfEligible()) {
                if (it.assignees() == null || it.assignees().isEmpty()) {
                    throw new BizException(ErrorCode.ORDER_ASSIGNEE_EMPTY,
                            "第 " + no + " 行【" + it.itemName() + "】没有指定设计师");
                }
                int sum = it.assignees().stream().mapToInt(OrderPricingRequest.Assignee::ratioBp).sum();
                if (!RatioUtil.isFull(sum)) {
                    // 提示里带上实际比例，主管才知道要补多少
                    throw new BizException(ErrorCode.RATIO_NOT_FULL, String.format(
                            "第 %d 行【%s】分成比例合计为 %s，必须正好 100%%",
                            no, it.itemName(), RatioUtil.bpToPercentText(sum)));
                }
                long distinct = it.assignees().stream()
                        .map(OrderPricingRequest.Assignee::employeeId).distinct().count();
                if (distinct != it.assignees().size()) {
                    throw new BizException(ErrorCode.BAD_REQUEST,
                            "第 " + no + " 行【" + it.itemName() + "】重复指定了同一个设计师");
                }
            }

            drafts.add(new ItemDraft(it, type, amount));
        }
        return drafts;
    }

    /**
     * 校验分成人存在且在在职。
     *
     * <p>【为什么必须查库确认在在职】
     * 派单选人的下拉只显示在职设计师，但接口是可以直接调的（前端改包、脚本导入）。
     * 一旦把业绩派给已离职的人，钱会进一个没人认领的账，且月底发现时要冲销重做。
     * 一次批量查询就能挡掉，不必省这一步。
     */
    private void checkAssignees(List<ItemDraft> drafts) {
        Set<Long> ids = new HashSet<>();
        for (ItemDraft d : drafts) {
            if (d.req().assignees() == null) {
                continue;
            }
            for (OrderPricingRequest.Assignee a : d.req().assignees()) {
                ids.add(a.employeeId());
            }
        }
        if (ids.isEmpty()) {
            return;
        }
        Map<Long, Employee> found = employeeMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Employee::getId, e -> e));

        for (Long id : ids) {
            Employee e = found.get(id);
            if (e == null) {
                throw new BizException(ErrorCode.NOT_FOUND, "分成人不存在：employeeId=" + id);
            }
            if (e.getStatus() != null && e.getStatus() == 0) {
                throw new BizException(ErrorCode.EMPLOYEE_RESIGNED,
                        "【" + e.getName() + "】已离职，不能作为分成人");
            }
        }
    }

    /**
     * 生成订单号：PO{yyMM}-{三位流水}。
     *
     * <p>取号走 FOR UPDATE 行锁（见 Mapper），并发提单时串行化；
     * 唯一索引 uk_order_no 是最后一道防线，万一锁没生效也不会出现重号。
     */
    private String nextOrderNo() {
        String prefix = "PO" + YearMonth.now().format(ORDER_NO_MONTH) + "-";
        String last = orderMapper.selectLastOrderNo(prefix);
        int seq = 1;
        if (last != null && last.length() > prefix.length()) {
            try {
                seq = Integer.parseInt(last.substring(prefix.length())) + 1;
            } catch (NumberFormatException e) {
                // 历史数据里有不符合规范的单号时，退回从 1 开始取；
                // 撞号由唯一索引兜底，不会写坏数据
                log.warn("订单号格式异常，从 001 重新开始：{}", last);
            }
        }
        return prefix + String.format("%03d", seq);
    }

    private String nextPaymentNo() {
        String prefix = "SK" + YearMonth.now().format(PAY_NO_MONTH) + "-";
        String last = paymentMapper.selectLastPaymentNo(prefix);
        int seq = 1;
        if (last != null && last.length() > prefix.length()) {
            try {
                seq = Integer.parseInt(last.substring(prefix.length())) + 1;
            } catch (NumberFormatException e) {
                log.warn("回款单号格式异常，从 001 重新开始：{}", last);
            }
        }
        return prefix + String.format("%03d", seq);
    }

    private SalesOrder requireOrder(Long orderId) {
        SalesOrder order = orderMapper.selectById(orderId);
        if (order == null || (order.getDeleted() != null && order.getDeleted() == 1)) {
            throw new BizException(ErrorCode.NOT_FOUND, "订单不存在");
        }
        return order;
    }

    /**
     * 一条待落库的明细：原始请求 + 解析出的类型 + 服务端算出的金额。
     *
     * <p>把"算过一次的结果"带下去，避免落库时再算一遍 ——
     * 同一份数据算两次就有可能算出两个结果（比如中间有人改了数量）。
     */
    private record ItemDraft(OrderPricingRequest.Item req, ItemType type, BigDecimal amount) {
    }
}
