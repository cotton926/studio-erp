package com.studio.erp.order.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.studio.erp.auth.CurrentUser;
import com.studio.erp.auth.CurrentUserHolder;
import com.studio.erp.auth.DataScope;
import com.studio.erp.auth.ScopeType;
import com.studio.erp.common.api.PageResult;
import com.studio.erp.common.enums.OrderStatus;
import com.studio.erp.common.enums.PaymentStatus;
import com.studio.erp.common.exception.BizException;
import com.studio.erp.common.exception.ErrorCode;
import com.studio.erp.order.dto.OrderDetailVO;
import com.studio.erp.order.dto.OrderListVO;
import com.studio.erp.order.dto.OrderQuery;
import com.studio.erp.order.entity.OrderItem;
import com.studio.erp.order.entity.SalesOrder;
import com.studio.erp.order.mapper.AssigneeRow;
import com.studio.erp.order.mapper.OrderItemAssigneeMapper;
import com.studio.erp.order.mapper.OrderItemMapper;
import com.studio.erp.order.mapper.SalesOrderMapper;
import com.studio.erp.performance.domain.ItemType;
import com.studio.erp.performance.domain.MoneyUtil;
import com.studio.erp.performance.domain.RatioUtil;
import com.studio.erp.performance.mapper.PerfQueryMapper;
import com.studio.erp.invoice.service.InvoiceService;
import com.studio.erp.receivable.service.ReceivableService;
import com.studio.erp.order.mapper.OrderStatusLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 订单查询：列表与详情。
 *
 * <p>【这个类里最值得看的是"遮蔽"逻辑】
 * 需求是"设计师仅能看到自己的那部分"。这件事有三种做法，只有一种是对的：
 *
 * <pre>
 * 1) 前端按角色隐藏列 —— 错。抓包就能看到，改一行 JS 就绕过。
 * 2) 后端把整单查出来，前端只显示自己那行 —— 错。数据和上面一样已经发出去了。
 * 3) 后端在返回前把不属于他的字段置空、把别人的分成行剔除 —— 对。
 * </pre>
 *
 * <p>这里采用 3)：设计师拿到的 JSON 里，totalAmount / paidAmount / unitPrice / amount
 * 全部是 null，别人的分成记录根本不在数组里。也就是说"泄露"这件事在协议层就不成立，
 * 不依赖前端自律，也不依赖协议加密。
 */
@Service
@RequiredArgsConstructor
public class OrderQueryService {

    /** 单页上限：防止有人传 pageSize=100000 把库拖垮 */
    private static final int MAX_PAGE_SIZE = 200;

    /**
     * 导出上限：5000 行。
     *
     * <p>【为什么要有上限】导出走的是"全部查出来再写 Excel"，
     * 十万行会把内存打满并让服务假死；而一次导出十万行，业务上从来没人真的逐行看过。
     * 超限时提示缩小日期范围，比让服务 OOM 后被运维重启要好。
     */
    private static final int EXPORT_MAX = 5000;

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(MoneyUtil.SCALE, RoundingMode.HALF_UP);

    private final SalesOrderMapper orderMapper;
    private final OrderItemMapper itemMapper;
    private final OrderItemAssigneeMapper assigneeMapper;
    private final PerfQueryMapper perfQueryMapper;
    private final OrderStatusLogMapper statusLogMapper;
    private final ReceivableService receivableService;
    private final InvoiceService invoiceService;
    /**
     * 订单分页列表。
     *
     * <p>数据范围是 TEAM 及以上看全量，SELF 只看自己参与的单。
     * 注意判断用的是 {@code covers(TEAM)} 而不是 {@code covers(ALL)} ——
     * 主管要给订单定价，看不到待定价的单就没法干活。
     */
    @DataScope(ScopeType.SELF)
    public PageResult<OrderListVO> page(OrderQuery query) {
        CurrentUser user = CurrentUserHolder.require();
        boolean selfOnly = !user.scope().covers(ScopeType.TEAM);
        Long selfId = selfOnly ? user.employeeId() : null;

        int pageNum = Math.max(query.getPageNum(), 1);
        int pageSize = Math.min(Math.max(query.getPageSize(), 1), MAX_PAGE_SIZE);

        IPage<OrderListVO> result = orderMapper.selectOrderPage(
                new Page<>(pageNum, pageSize), query, selfId, selfId);

        List<OrderListVO> list = new ArrayList<>(result.getRecords().size());
        for (OrderListVO vo : result.getRecords()) {
            vo.setStatusText(OrderStatus.of(vo.getStatus()).getLabel());
            vo.setPaymentStatusText(PaymentStatus.of(vo.getPaymentStatus()).getLabel());
            if (selfOnly) {
                // 设计师：看不到订单总额，也看不到客户已付多少（那是商务信息）
                vo.setTotalAmount(null);
                vo.setPaidAmount(null);
            } else {
                // 主管：看总额和已收，不需要"我能分多少"这一列
                vo.setMyAmount(null);
                if (vo.getPaidAmount() == null) {
                    vo.setPaidAmount(ZERO);
                }
            }
            list.add(vo);
        }
        return PageResult.of(pageNum, pageSize, result.getTotal(), list);
    }

    /**
     * 导出用的订单列表（不分页，有上限）。
     *
     * <p>【为什么不复用 {@link #page}】page 会按角色隐藏金额（设计师看不到总额），
     * 而导出场景只开放给 TEAM 及以上，隐藏逻辑反而会让导出的表缺列。
     * 两条路径各自明确自己的口径，比在一个方法里塞 if 更不容易出错。
     */
    public List<OrderListVO> exportList(OrderQuery query) {
        CurrentUser user = CurrentUserHolder.require();
        if (!user.scope().covers(ScopeType.TEAM)) {
            throw new BizException(ErrorCode.FORBIDDEN, "没有导出订单台账的权限");
        }
        query.setPageNum(1);
        query.setPageSize(EXPORT_MAX + 1);
        IPage<OrderListVO> result = orderMapper.selectOrderPage(
                new Page<>(1, EXPORT_MAX + 1), query, null, null);
        if (result.getTotal() > EXPORT_MAX) {
            throw new BizException(ErrorCode.EXPORT_ROW_LIMIT,
                    "符合条件的订单超过 " + EXPORT_MAX + " 条，请缩小日期范围后再导出");
        }
        for (OrderListVO vo : result.getRecords()) {
            vo.setStatusText(OrderStatus.of(vo.getStatus()).getLabel());
            vo.setPaymentStatusText(PaymentStatus.of(vo.getPaymentStatus()).getLabel());
            if (vo.getPaidAmount() == null) {
                vo.setPaidAmount(ZERO);
            }
        }
        return result.getRecords();
    }

    /**
     * 订单详情。
     * <p>【设计师为什么能看整单的工作内容】
     * 因为他要干活：一张画册单里如果还有两页文案是别人做的，他必须知道边界在哪。
     * 所以明细名称、数量、规格对他开放，只有金额对他关闭。
     * 这比"整单对他不可见"更实用 —— 后者会让他为了确认一条需求去问客服，效率更低。
     */
    @DataScope(ScopeType.SELF)
    public OrderDetailVO detail(Long orderId) {
        CurrentUser user = CurrentUserHolder.require();
        SalesOrder order = orderMapper.selectById(orderId);
        if (order == null || (order.getDeleted() != null && order.getDeleted() == 1)) {
            throw new BizException(ErrorCode.NOT_FOUND, "订单不存在");
        }

        boolean selfOnly = !user.scope().covers(ScopeType.TEAM);
        List<OrderItem> items = itemMapper.selectByOrder(orderId);
        List<AssigneeRow> assignees = assigneeMapper.selectByOrder(orderId);

        if (selfOnly) {
            boolean participated = assignees.stream()
                    .anyMatch(a -> user.employeeId() != null && user.employeeId().equals(a.employeeId()));
            if (!participated) {
                // 查别人不相关的单：报错而不是返回空单。
                // 返回空单会让调用方以为"这单没有明细"，而实际上是没权限，两种情况的处理方式完全不同。
                throw new BizException(ErrorCode.FORBIDDEN, "只能查看自己参与的订单");
            }
        }

        Map<Long, List<AssigneeRow>> byItem = new LinkedHashMap<>();
        for (AssigneeRow a : assignees) {
            byItem.computeIfAbsent(a.orderItemId(), k -> new ArrayList<>()).add(a);
        }

        BigDecimal paid = perfQueryMapper.selectAllocatedAmount(orderId, null);
        BigDecimal mine = ZERO;

        OrderDetailVO vo = new OrderDetailVO();
        vo.setId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setClientName(order.getClientName());
        vo.setTitle(order.getTitle());
        vo.setCategory(order.getCategory());
        vo.setStatus(order.getStatus());
        vo.setStatusText(OrderStatus.of(order.getStatus()).getLabel());
        vo.setPaymentStatus(order.getPaymentStatus());
        vo.setPaymentStatusText(PaymentStatus.of(order.getPaymentStatus()).getLabel());
        vo.setSignDate(order.getSignDate());
        vo.setExpectDeliverDate(order.getExpectDeliverDate());
        vo.setActualDeliverDate(order.getActualDeliverDate());
        vo.setRemark(order.getRemark());

        List<OrderDetailVO.ItemVO> itemVOs = new ArrayList<>(items.size());
        for (OrderItem item : items) {
            List<AssigneeRow> rows = byItem.getOrDefault(item.getId(), List.of());
            boolean mineItem = selfOnly && rows.stream()
                    .anyMatch(r -> user.employeeId() != null && user.employeeId().equals(r.employeeId()));

            OrderDetailVO.ItemVO iv = new OrderDetailVO.ItemVO();
            iv.setId(item.getId());
            iv.setLineNo(item.getLineNo());
            iv.setItemName(item.getItemName());
            iv.setSpec(item.getSpec());
            iv.setUnit(item.getUnit());
            iv.setQty(item.getQty());
            iv.setItemType(item.getItemType());
            iv.setItemTypeText(ItemType.of(item.getItemType()).getLabel());
            iv.setRemark(item.getRemark());
            iv.setMine(mineItem);

            if (selfOnly) {
                // 金额对设计师一律关闭：单价、小计都不给
                iv.setUnitPrice(null);
                iv.setAmount(null);
            } else {
                iv.setUnitPrice(item.getUnitPrice());
                iv.setAmount(item.getAmount());
            }

            List<OrderDetailVO.AssigneeVO> avs = new ArrayList<>();
            for (AssigneeRow r : rows) {
                boolean isMe = user.employeeId() != null && user.employeeId().equals(r.employeeId());
                if (selfOnly && !isMe) {
                    // 别人的分成行直接不返回，而不是返回 null 金额 ——
                    // 连"这一行有三个人分"这种信息都不给，避免被拿去推测同事收入
                    continue;
                }
                OrderDetailVO.AssigneeVO av = new OrderDetailVO.AssigneeVO();
                av.setEmployeeId(r.employeeId());
                av.setEmployeeName(r.employeeName());
                av.setAssigneeRole(r.assigneeRole());
                av.setRatioBp(r.ratioBp());
                av.setRatioText(RatioUtil.bpToPercentText(r.ratioBp()));
                avs.add(av);

                if (isMe) {
                    // 本单我的分成 = Σ(能参与业绩的行金额 × 我的比例)
                    ItemType type = ItemType.of(item.getItemType());
                    if (type.isPerfEligible() && item.getAmount() != null) {
                        mine = mine.add(MoneyUtil.divide(
                                item.getAmount().multiply(BigDecimal.valueOf(r.ratioBp())),
                                BigDecimal.valueOf(RatioUtil.FULL_BP)));
                    }
                }
            }
            iv.setAssignees(avs);
            itemVOs.add(iv);
        }
        vo.setItems(itemVOs);

        if (selfOnly) {
            vo.setMyAmount(mine);
            vo.setTotalAmount(null);
            vo.setPaidAmount(null);
            vo.setUnpaidAmount(null);
        } else {
            BigDecimal total = order.getTotalAmount() == null ? ZERO : order.getTotalAmount();
            BigDecimal paidAmount = paid == null ? ZERO : paid;
            vo.setTotalAmount(total);
            vo.setPaidAmount(paidAmount);
            vo.setUnpaidAmount(total.subtract(paidAmount));
            vo.setMyAmount(null);
            // 收款计划与开票记录都带金额，只在"能看金额"的分支里查，
            // 设计师的响应里这两个字段根本不存在 —— 不是置空，是不查
            vo.setPlans(receivableService.plans(orderId));
            vo.setInvoices(invoiceService.orderInvoices(orderId));
        }
        vo.setTimeline(timeline(orderId));
        return vo;
    }

    /** 状态时间线，两种人看到的是同一份（不含金额） */
    private List<OrderDetailVO.TimelineVO> timeline(Long orderId) {
        return statusLogMapper.selectTimeline(orderId).stream().map(row -> {
            OrderDetailVO.TimelineVO tv = new OrderDetailVO.TimelineVO();
            tv.setFromStatus(row.fromStatus());
            tv.setFromStatusText(row.fromStatus() == null ? null : OrderStatus.of(row.fromStatus()).getLabel());
            tv.setToStatus(row.toStatus());
            tv.setToStatusText(OrderStatus.of(row.toStatus()).getLabel());
            tv.setOperatorId(row.operatorId());
            tv.setOperatorName(row.operatorName() == null ? "系统" : row.operatorName());
            tv.setRemark(row.remark());
            tv.setCreatedAt(row.createdAt());
            return tv;
        }).toList();
    }
}
