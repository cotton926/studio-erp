package com.studio.erp.report.controller;

import com.studio.erp.auth.RequireRole;
import com.studio.erp.common.excel.ExcelWriter;
import com.studio.erp.order.dto.OrderQuery;
import com.studio.erp.report.dto.InvoiceExportRow;
import com.studio.erp.report.dto.OrderExportRow;
import com.studio.erp.report.dto.PerformanceExportRow;
import com.studio.erp.report.dto.ReceivableExportRow;
import com.studio.erp.report.dto.SalaryExportRow;
import com.studio.erp.report.service.ReportService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * 报表导出接口（全部返回 xlsx 文件流）。
 *
 * <p>【为什么用 GET + 查询参数，而不是 POST 一个导出条件对象】
 * GET 的语义是"取一份数据"，导出正是如此；而且参数少、全在 URL 里，
 * 排查"导出的数字为什么不对"时，用户直接把链接发过来就能复现。
 * POST 导出在日志里只剩一个 /export，什么条件都看不到。
 *
 * <p>【权限为什么各自不同】
 * 工资表最敏感（只有管理员和主管）；订单台账与开票台账涉及客户金额，
 * 财务要能导；业绩明细人人可导，但只能是自己的（服务层已强制）。
 * 用一套"导出权限"统一放开，等于给每个人开了所有数据的后门。
 */
@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    /**
     * 工资表。
     *
     * @param period  工资期间 yyyy-MM，默认当月
     * @param keyword 姓名 / 工号
     */
    @GetMapping("/salary")
    @RequireRole({"ADMIN", "SUPERVISOR"})
    public void salary(@RequestParam(required = false) String period,
                       @RequestParam(required = false) String keyword,
                       HttpServletResponse response) {
        String p = period == null || period.isBlank() ? YearMonth.now().toString() : period.trim();
        List<SalaryExportRow> rows = reportService.salaryRows(p, keyword);
        ExcelWriter.write(response, "工资表-" + p + ".xlsx", "工资表", SalaryExportRow.class, rows);
    }

    /**
     * 订单台账（与列表页相同的筛选条件）。
     */
    @GetMapping("/orders")
    @RequireRole({"ADMIN", "SUPERVISOR", "FINANCE"})
    public void orders(@RequestParam(required = false) String keyword,
                       @RequestParam(required = false) Integer status,
                       @RequestParam(required = false) Integer paymentStatus,
                       @RequestParam(required = false)
                       @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
                       @RequestParam(required = false)
                       @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
                       HttpServletResponse response) {
        OrderQuery query = new OrderQuery();
        query.setKeyword(keyword);
        query.setStatus(status);
        query.setPaymentStatus(paymentStatus);
        query.setDateFrom(dateFrom);
        query.setDateTo(dateTo);
        List<OrderExportRow> rows = reportService.orderRows(query);
        ExcelWriter.write(response, "订单台账-" + ReportService.today() + ".xlsx",
                "订单台账", OrderExportRow.class, rows);
    }

    /**
     * 催收清单。
     *
     * @param scope WINDOW / OVERDUE / ALL，与页面当前页签保持一致
     */
    @GetMapping("/receivables")
    @RequireRole({"ADMIN", "SUPERVISOR", "FINANCE", "CS"})
    public void receivables(@RequestParam(required = false) String scope,
                            @RequestParam(required = false) String keyword,
                            HttpServletResponse response) {
        List<ReceivableExportRow> rows = reportService.receivableRows(scope, keyword);
        ExcelWriter.write(response, "催收清单-" + ReportService.today() + ".xlsx",
                "催收清单", ReceivableExportRow.class, rows);
    }

    /** 开票台账 */
    @GetMapping("/invoices")
    @RequireRole({"ADMIN", "SUPERVISOR", "FINANCE"})
    public void invoices(@RequestParam(required = false) Integer status,
                         @RequestParam(required = false) String period,
                         @RequestParam(required = false) String keyword,
                         HttpServletResponse response) {
        List<InvoiceExportRow> rows = reportService.invoiceRows(status, period, keyword);
        String suffix = period == null || period.isBlank() ? ReportService.today() : period.trim();
        ExcelWriter.write(response, "开票台账-" + suffix + ".xlsx",
                "开票台账", InvoiceExportRow.class, rows);
    }

    /** 我的业绩明细：任何人可导，但只含自己参与的单 */
    @GetMapping("/performance")
    public void performance(@RequestParam(required = false) String period,
                            HttpServletResponse response) {
        List<PerformanceExportRow> rows = reportService.performanceRows(period);
        String suffix = period == null || period.isBlank() ? YearMonth.now().toString() : period.trim();
        ExcelWriter.write(response, "我的业绩-" + suffix + ".xlsx",
                "我的业绩", PerformanceExportRow.class, rows);
    }
}
