package com.studio.erp.invoice.service;

import com.studio.erp.common.audit.AuditService;
import com.studio.erp.common.api.PageResult;
import com.studio.erp.common.enums.OrderStatus;
import com.studio.erp.common.exception.BizException;
import com.studio.erp.common.exception.ErrorCode;
import com.studio.erp.invoice.domain.InvoiceStatus;
import com.studio.erp.invoice.domain.InvoiceType;
import com.studio.erp.invoice.domain.TaxSplit;
import com.studio.erp.invoice.dto.InvoiceApplyRequest;
import com.studio.erp.invoice.dto.InvoiceIssueRequest;
import com.studio.erp.invoice.dto.InvoiceSummaryVO;
import com.studio.erp.invoice.dto.InvoiceVO;
import com.studio.erp.invoice.entity.Invoice;
import com.studio.erp.invoice.mapper.InvoiceMapper;
import com.studio.erp.invoice.mapper.InvoiceRow;
import com.studio.erp.order.entity.SalesOrder;
import com.studio.erp.order.mapper.SalesOrderMapper;
import com.studio.erp.performance.domain.MoneyUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * 开票服务。
 *
 * <p>【三条不可绕过的规则】
 * <pre>
 * 1) 只能给"已定价"的订单开票 —— 待定价的单总额还是 0，开出去就是空头发票；
 * 2) 同一订单累计有效开票金额 ≤ 订单总额 —— 多开等于虚开，税务风险；
 * 3) 发票号全局唯一 —— 一张票号不可能对应两张票。
 * </pre>
 *
 * <p>【为什么"作废"是改状态而不是删记录】
 * 发票是税务凭证，删掉就查不到"曾经开过又作废"这件事。
 * 作废记录留着，才能在税务核查时说明"这张票为什么不算收入"。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceService {

    /** 默认税率 6%：设计服务属于现代服务业，这是最常见的档位 */
    private static final BigDecimal DEFAULT_TAX_PERCENT = new BigDecimal("6");
    /** 台账单页上限（与订单列表同口径） */
    private static final int MAX_PAGE_SIZE = 200;
    /** 导出行数上限 */
    static final int EXPORT_MAX = 5000;

    private final InvoiceMapper invoiceMapper;
    private final SalesOrderMapper orderMapper;
    private final AuditService auditService;

    // =====================================================================
    // 申请 / 开票 / 作废
    // =====================================================================

    /**
     * 申请开票。
     *
     * <p>【为什么申请和开票分成两步】
     * 申请人（客服/主管）知道客户要什么抬头、什么金额；财务知道票什么时候能开出来。
     * 合成一步的结果是客服被迫等财务在场才能提交，或者财务替客服猜抬头。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long apply(InvoiceApplyRequest req) {
        if (req.orderId() == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "请先选择要开票的订单");
        }
        SalesOrder order = orderMapper.selectById(req.orderId());
        if (order == null || (order.getDeleted() != null && order.getDeleted() == 1)) {
            throw new BizException(ErrorCode.NOT_FOUND, "订单不存在");
        }
        OrderStatus status = OrderStatus.of(order.getStatus());
        if (status != OrderStatus.CONFIRMED && status != OrderStatus.IN_PROGRESS
                && status != OrderStatus.DELIVERED) {
            throw new BizException(ErrorCode.ORDER_NOT_INVOICEABLE);
        }

        BigDecimal amount = MoneyUtil.yuan(req.amount());
        if (amount.signum() <= 0) {
            throw new BizException(ErrorCode.INVOICE_AMOUNT_INVALID);
        }

        BigDecimal invoiced = nz(invoiceMapper.sumValidByOrder(order.getId()));
        BigDecimal total = nz(order.getTotalAmount());
        if (invoiced.add(amount).compareTo(total) > 0) {
            BigDecimal left = total.subtract(invoiced);
            throw new BizException(ErrorCode.INVOICE_OVER_AMOUNT, String.format(
                    "本次开票 %s 元，加上已开的 %s 元会超过订单总额 %s 元；本单最多还能开 %s 元",
                    amount.toPlainString(), invoiced.toPlainString(),
                    total.toPlainString(), left.toPlainString()));
        }

        InvoiceType type;
        try {
            type = InvoiceType.of(req.invoiceType());
        } catch (IllegalArgumentException e) {
            throw new BizException(ErrorCode.BAD_REQUEST, "发票类型只能是 1（普通）或 2（专用）");
        }

        Invoice invoice = new Invoice();
        invoice.setOrderId(order.getId());
        invoice.setClientId(order.getClientId());
        invoice.setTitle(StringUtils.hasText(req.title()) ? req.title().trim() : order.getClientName());
        invoice.setTaxNo(StringUtils.hasText(req.taxNo()) ? req.taxNo().trim() : null);
        invoice.setAmount(amount);
        invoice.setTaxRate(toRate(req.taxRatePercent()));
        invoice.setInvoiceType(type.getCode());
        invoice.setContent(StringUtils.hasText(req.content()) ? req.content().trim() : "设计服务费");
        invoice.setStatus(InvoiceStatus.PENDING.getCode());
        invoice.setApplyBy(com.studio.erp.auth.CurrentUserHolder.require().employeeId());
        invoice.setAppliedAt(LocalDateTime.now());
        invoice.setRemark(req.remark());
        invoiceMapper.insert(invoice);

        auditService.event("INVOICE", invoice.getId(), String.format("申请开票 %s 元（%s）",
                amount.toPlainString(), type.getLabel()));
        log.info("申请开票 invoiceId={} orderNo={} 金额={} 类型={}", invoice.getId(),
                order.getOrderNo(), amount.toPlainString(), type.getLabel());
        return invoice.getId();
    }

    /**
     * 财务回填发票号，标记为已开。
     *
     * <p>【为什么要单独校验发票号唯一，明明表上有唯一索引】
     * 唯一索引报出的是 "Duplicate entry 'xxx' for key 'uk_invoice_no'"，
     * 用户看到这串英文只会来问"系统坏了"。先查一次给出中文提示，
     * 索引作为并发下的最后一道防线。
     */
    @Transactional(rollbackFor = Exception.class)
    public void issue(Long id, InvoiceIssueRequest req) {
        Invoice invoice = requireInvoice(id);
        if (invoice.getStatus() == null || invoice.getStatus() != InvoiceStatus.PENDING.getCode()) {
            throw new BizException(ErrorCode.INVOICE_STATUS_INVALID,
                    "只有「待开」的申请可以开票，当前状态：" + InvoiceStatus.labelOf(invoice.getStatus()));
        }
        String no = req.invoiceNo() == null ? "" : req.invoiceNo().trim();
        if (no.isEmpty()) {
            throw new BizException(ErrorCode.INVOICE_NO_REQUIRED);
        }
        if (invoiceMapper.countByInvoiceNo(no, id) > 0) {
            throw new BizException(ErrorCode.INVOICE_NO_DUPLICATED);
        }

        invoice.setInvoiceNo(no);
        invoice.setIssueDate(req.issueDate() == null ? LocalDate.now() : req.issueDate());
        invoice.setStatus(InvoiceStatus.ISSUED.getCode());
        invoiceMapper.updateById(invoice);

        auditService.event("INVOICE", id, "开票完成 票号 " + no);
        log.info("开票完成 invoiceId={} 票号={} 开票日={}", id, no, invoice.getIssueDate());
    }

    /**
     * 作废发票。
     *
     * <p>原因必填：作废是"这张票不算数"的声明，没有原因，三个月后没人能解释
     * 为什么这张票被作废了 —— 而税务核查问的正是这个。
     */
    @Transactional(rollbackFor = Exception.class)
    public void voidInvoice(Long id, String reason) {
        Invoice invoice = requireInvoice(id);
        if (invoice.getStatus() != null && invoice.getStatus() == InvoiceStatus.VOIDED.getCode()) {
            throw new BizException(ErrorCode.INVOICE_STATUS_INVALID, "该发票已经作废，无需重复操作");
        }
        if (!StringUtils.hasText(reason)) {
            throw new BizException(ErrorCode.INVOICE_VOID_REASON_REQUIRED);
        }
        invoice.setStatus(InvoiceStatus.VOIDED.getCode());
        invoice.setVoidReason(reason.trim());
        invoiceMapper.updateById(invoice);

        auditService.event("INVOICE", id, "作废发票：" + reason.trim()
                + (invoice.getInvoiceNo() == null ? "" : "（票号 " + invoice.getInvoiceNo() + "）"));
        log.info("作废发票 invoiceId={} 原因={}", id, reason.trim());
    }

    // =====================================================================
    // 查询
    // =====================================================================

    /**
     * 开票台账。
     *
     * @param status  1/2/3，为空查全部
     * @param period  yyyy-MM，按申请时间筛选
     */
    public PageResult<InvoiceVO> page(Integer status, String period, String keyword, int pageNum, int pageSize) {
        PeriodBounds bounds = periodBounds(period);
        long pn = Math.max(pageNum, 1);
        long ps = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        List<InvoiceRow> rows = invoiceMapper.selectRows((int) ps, (pn - 1) * ps, status, null,
                bounds.from(), bounds.to(), keywordOrNull(keyword));
        List<InvoiceVO> list = rows.stream().map(this::toVO).toList();
        long total = invoiceMapper.countRows(status, null, bounds.from(), bounds.to(), keywordOrNull(keyword));
        return PageResult.of(pn, ps, total, list);
    }

    /**
     * 导出用的台账（不分页，有上限）。
     *
     * <p>【为什么超额报错而不是截断】开票是财务数据，悄悄截断的导出表
     * 看起来完整、实际缺行，财务对不上账只会怀疑系统 —— 明确报错让用户缩小范围更诚实。
     */
    public List<InvoiceVO> exportList(Integer status, String period, String keyword) {
        PeriodBounds bounds = periodBounds(period);
        // 多取一行只为判断"是否超上限"，不需要 count
        List<InvoiceRow> rows = invoiceMapper.selectRows(EXPORT_MAX + 1, 0,
                status, null, bounds.from(), bounds.to(), keywordOrNull(keyword));
        if (rows.size() > EXPORT_MAX) {
            throw new BizException(ErrorCode.BAD_REQUEST, String.format(
                    "符合条件的开票记录超过 %d 条，请缩小筛选范围（如按月份）后再导出", EXPORT_MAX));
        }
        return rows.stream().map(this::toVO).toList();
    }

    /** 某张订单的开票记录（订单详情用） */
    public List<InvoiceVO> orderInvoices(Long orderId) {
        return invoiceMapper.selectRows(null, 0, null, orderId, null, null, null)
                .stream().map(this::toVO).toList();
    }

    /** period（yyyy-MM）→ 左闭右开时间区间；为空返回全 null */
    private PeriodBounds periodBounds(String period) {
        if (!StringUtils.hasText(period)) {
            return new PeriodBounds(null, null);
        }
        YearMonth month;
        try {
            month = YearMonth.parse(period.trim());
        } catch (DateTimeParseException e) {
            throw new BizException(ErrorCode.PERIOD_INVALID);
        }
        LocalDateTime from = month.atDay(1).atStartOfDay();
        // 用"下月 1 日 0 点"作为右开区间：比月底 23:59:59 更严谨，
        // 不会漏掉当天最后一秒入库的记录
        LocalDateTime to = month.plusMonths(1).atDay(1).atStartOfDay();
        return new PeriodBounds(from, to);
    }

    private record PeriodBounds(LocalDateTime from, LocalDateTime to) {
    }

    private String keywordOrNull(String keyword) {
        return StringUtils.hasText(keyword) ? keyword.trim() : null;
    }

    /** 开票概览 */
    public InvoiceSummaryVO summary() {
        InvoiceMapper.InvoiceSum pending = invoiceMapper.sumByStatus(InvoiceStatus.PENDING.getCode());
        LocalDate today = LocalDate.now();
        InvoiceMapper.InvoiceSum month = invoiceMapper.sumIssuedBetween(
                today.withDayOfMonth(1), today.withDayOfMonth(today.lengthOfMonth()));
        InvoiceMapper.InvoiceSum issued = invoiceMapper.sumByStatus(InvoiceStatus.ISSUED.getCode());
        return new InvoiceSummaryVO(
                pending == null || pending.getCnt() == null ? 0 : pending.getCnt(),
                pending == null ? BigDecimal.ZERO : MoneyUtil.yuan(pending.getAmt()),
                month == null || month.getCnt() == null ? 0 : month.getCnt(),
                month == null ? BigDecimal.ZERO : MoneyUtil.yuan(month.getAmt()),
                issued == null ? BigDecimal.ZERO : MoneyUtil.yuan(issued.getAmt()));
    }

    // =====================================================================
    // 内部
    // =====================================================================

    private InvoiceVO toVO(InvoiceRow r) {
        BigDecimal amount = nz(r.getAmount());
        BigDecimal rate = r.getTaxRate() == null ? BigDecimal.ZERO : r.getTaxRate();
        // 税额按"价内税"拆：含税金额 / (1+税率) 是不含税金额，差额即税额。
        // 直接把 amount × 税率 会多算 6%，财务一眼就能看出不对
        TaxSplit split = TaxSplit.of(amount, rate);

        return new InvoiceVO(
                r.getId(), r.getOrderId(), r.getOrderNo(), r.getClientName(),
                r.getTitle(), r.getTaxNo(), r.getInvoiceNo(),
                amount,
                rate.signum() <= 0 ? BigDecimal.ZERO : rate.multiply(BigDecimal.valueOf(100))
                        .setScale(2, RoundingMode.HALF_UP),
                split.tax(), split.net(),
                r.getInvoiceType(), InvoiceType.labelOf(r.getInvoiceType()),
                r.getContent(), r.getIssueDate(),
                r.getStatus(), InvoiceStatus.labelOf(r.getStatus()),
                r.getApplyBy(), r.getApplyByName(), r.getAppliedAt(),
                r.getVoidReason(), r.getRemark(), r.getCreatedAt(),
                r.getOrderTotal(), r.getOrderInvoiced(), r.getOrderPaid());
    }

    private Invoice requireInvoice(Long id) {
        Invoice invoice = invoiceMapper.selectById(id);
        if (invoice == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "开票记录不存在");
        }
        return invoice;
    }

    /** 百分数 → 小数（6 → 0.0600），并做合理区间校验，防止把 0.06 当成 0.06% 存进去 */
    private BigDecimal toRate(BigDecimal percent) {
        BigDecimal p = percent == null ? DEFAULT_TAX_PERCENT : percent;
        if (p.signum() < 0 || p.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new BizException(ErrorCode.BAD_REQUEST, "税率应在 0 ~ 100 之间（填 6 表示 6%）");
        }
        return p.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO.setScale(MoneyUtil.SCALE, RoundingMode.HALF_UP) : v;
    }
}
