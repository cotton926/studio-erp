package com.studio.erp.report.service;

import com.studio.erp.common.audit.AuditService;
import com.studio.erp.common.exception.BizException;
import com.studio.erp.common.exception.ErrorCode;
import com.studio.erp.invoice.dto.InvoiceVO;
import com.studio.erp.invoice.service.InvoiceService;
import com.studio.erp.order.dto.OrderListVO;
import com.studio.erp.order.dto.OrderQuery;
import com.studio.erp.order.service.OrderQueryService;
import com.studio.erp.performance.domain.MoneyUtil;
import com.studio.erp.performance.dto.PerfSummaryVO;
import com.studio.erp.performance.service.PerformanceQueryService;
import com.studio.erp.receivable.dto.CollectionVO;
import com.studio.erp.receivable.service.ReceivableService;
import com.studio.erp.report.dto.InvoiceExportRow;
import com.studio.erp.report.dto.OrderExportRow;
import com.studio.erp.report.dto.PerformanceExportRow;
import com.studio.erp.report.dto.ReceivableExportRow;
import com.studio.erp.report.dto.SalaryExportRow;
import com.studio.erp.salary.dto.SalaryVO;
import com.studio.erp.salary.service.SalaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 报表导出：把页面上的数据翻译成"可以直接交给别人看的表格"。
 *
 * <p>【为什么导出服务单独一层，不写在 Controller 里】
 * 每一张表的列都经过一次删减（去掉给前端做交互的字段）、一次补算（未收、税额）、
 * 一次口径确认（金额取谁的视角）。这些是业务判断，不是参数搬运；
 * 塞进 Controller 会让"接口"和"报表口径"混在一起，改一个列名要动接口层。
 *
 * <p>【为什么每次导出都写审计】
 * 工资表、客户金额这些数据一旦导出就离开了系统，之后流到哪里系统管不到。
 * 至少要知道"谁在什么时候导了哪张表、多少行" —— 这是内部系统最低限度的交代。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(MoneyUtil.SCALE, RoundingMode.HALF_UP);

    private final SalaryService salaryService;
    private final OrderQueryService orderQueryService;
    private final ReceivableService receivableService;
    private final InvoiceService invoiceService;
    private final PerformanceQueryService performanceQueryService;
    private final AuditService auditService;

    /** 工资表 */
    public List<SalaryExportRow> salaryRows(String period, String keyword) {
        List<SalaryVO> list = salaryService.teamSalary(period, keyword);
        List<SalaryExportRow> rows = new ArrayList<>(list.size());
        for (SalaryVO v : list) {
            SalaryExportRow r = new SalaryExportRow();
            r.setEmpNo(v.getEmpNo());
            r.setEmployeeName(v.getEmployeeName());
            r.setDeptName(v.getDeptName());
            r.setEmpLevelLabel(v.getEmpLevelLabel());
            r.setPerformance(nz(v.getPerformance()));
            r.setRateText(v.getRateText());
            r.setCommission(nz(v.getCommission()));
            r.setBaseSalary(nz(v.getBaseSalary()));
            r.setBaseSalaryNote(v.getBaseSalaryNote());
            r.setPayable(nz(v.getPayable()));
            // 口径列把"跳点/累进"和"命中哪一档"放在一起，事后复核工资时不用再问人
            r.setTierRemark((v.getTierModeLabel() == null ? "" : v.getTierModeLabel() + "；")
                    + (v.getTierRemark() == null ? "" : v.getTierRemark()));
            r.setPeriod(v.getPeriod());
            rows.add(r);
        }
        audit("导出工资表", period, rows.size());
        return rows;
    }

    /** 订单台账 */
    public List<OrderExportRow> orderRows(OrderQuery query) {
        List<OrderListVO> list = orderQueryService.exportList(query);
        List<OrderExportRow> rows = new ArrayList<>(list.size());
        for (OrderListVO v : list) {
            OrderExportRow r = new OrderExportRow();
            r.setOrderNo(v.getOrderNo());
            r.setClientName(v.getClientName());
            r.setTitle(v.getTitle());
            r.setCategory(v.getCategory());
            r.setSignDate(v.getSignDate());
            r.setStatusText(v.getStatusText());
            r.setPaymentStatusText(v.getPaymentStatusText());
            BigDecimal total = nz(v.getTotalAmount());
            BigDecimal paid = nz(v.getPaidAmount());
            r.setTotalAmount(total);
            r.setPaidAmount(paid);
            r.setUnpaidAmount(total.subtract(paid));
            r.setDesignerNames(v.getDesignerNames());
            r.setExpectDeliverDate(v.getExpectDeliverDate());
            rows.add(r);
        }
        audit("导出订单台账", null, rows.size());
        return rows;
    }

    /** 催收清单导出行数上限：与订单导出同口径，防止把整库拖进内存 */
    private static final int EXPORT_MAX = 5000;

    /** 催收清单 */
    public List<ReceivableExportRow> receivableRows(String scope, String keyword) {
        List<CollectionVO> list = receivableService.collection(scope, keyword);
        if (list.size() > EXPORT_MAX) {
            throw new BizException(ErrorCode.BAD_REQUEST, String.format(
                    "符合条件的催收记录超过 %d 条，请缩小筛选范围（如选「窗口内」或「逾期」）后再导出", EXPORT_MAX));
        }
        List<ReceivableExportRow> rows = new ArrayList<>(list.size());
        for (CollectionVO v : list) {
            ReceivableExportRow r = new ReceivableExportRow();
            r.setOrderNo(v.orderNo());
            r.setClientName(v.clientName());
            r.setTitle(v.title());
            r.setPlanName(v.planName());
            r.setPlanAmount(nz(v.planAmount()));
            r.setReceivedAmount(nz(v.receivedAmount()));
            r.setUnpaidAmount(nz(v.unpaidAmount()));
            r.setPlanDate(v.planDate());
            r.setOverdueDays(v.overdueDays());
            r.setRemindCount(v.remindCount());
            r.setLastRemindAt(v.lastRemindAt());
            r.setPromiseDate(v.promiseDate());
            r.setCsName(v.csName());
            r.setCsPhone(v.csPhone());
            rows.add(r);
        }
        audit("导出催收清单", null, rows.size());
        return rows;
    }

    /** 开票台账 */
    public List<InvoiceExportRow> invoiceRows(Integer status, String period, String keyword) {
        // exportList 自带 5000 行上限与超额报错（searchCount=false，不浪费一次 count）
        List<InvoiceVO> list = invoiceService.exportList(status, period, keyword);
        List<InvoiceExportRow> rows = new ArrayList<>(list.size());
        for (InvoiceVO v : list) {
            InvoiceExportRow r = new InvoiceExportRow();
            r.setInvoiceNo(v.invoiceNo());
            r.setOrderNo(v.orderNo());
            r.setClientName(v.clientName());
            r.setTitle(v.title());
            r.setInvoiceTypeText(v.invoiceTypeText());
            r.setAmount(nz(v.amount()));
            r.setTaxRateText(v.taxRatePercent() == null || v.taxRatePercent().signum() == 0
                    ? "—" : v.taxRatePercent().stripTrailingZeros().toPlainString() + "%");
            r.setTaxAmount(nz(v.taxAmount()));
            r.setNetAmount(nz(v.netAmount()));
            r.setIssueDate(v.issueDate());
            r.setStatusText(v.statusText());
            r.setApplyByName(v.applyByName());
            r.setAppliedAt(v.appliedAt());
            r.setVoidReason(v.voidReason());
            rows.add(r);
        }
        audit("导出开票台账", period, rows.size());
        return rows;
    }

    /** 我的业绩明细（数据范围由 PerformanceQueryService 兜住，只会是自己） */
    public List<PerformanceExportRow> performanceRows(String period) {
        PerfSummaryVO summary = performanceQueryService.summary(period);
        List<PerformanceExportRow> rows = new ArrayList<>(summary.getOrders().size());
        for (PerfSummaryVO.MyOrder o : summary.getOrders()) {
            PerformanceExportRow r = new PerformanceExportRow();
            r.setOrderNo(o.orderNo());
            r.setClientName(o.clientName());
            r.setTitle(o.title());
            r.setSignDate(o.signDate());
            r.setStatusText(o.statusText());
            r.setPaymentStatusText(o.paymentStatusText());
            r.setRatioText(o.ratioText());
            r.setMySignAmount(nz(o.mySignAmount()));
            r.setMyPaidAmount(nz(o.myPaidAmount()));
            r.setMyUnpaid(nz(o.myUnpaid()));
            rows.add(r);
        }
        audit("导出我的业绩", summary.getPeriod(), rows.size());
        return rows;
    }

    /** 提示用的文件名日期后缀 */
    public static String today() {
        return LocalDate.now().toString();
    }

    private void audit(String what, String period, int rows) {
        auditService.event("REPORT", 0L, what + (period == null ? "" : " " + period) + " · " + rows + " 行");
        log.info("{} period={} rows={}", what, period, rows);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? ZERO : v;
    }
}
