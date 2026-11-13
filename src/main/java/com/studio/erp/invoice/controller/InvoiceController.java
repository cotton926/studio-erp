package com.studio.erp.invoice.controller;

import com.studio.erp.auth.RequireRole;
import com.studio.erp.common.api.PageResult;
import com.studio.erp.common.api.R;
import com.studio.erp.invoice.dto.InvoiceApplyRequest;
import com.studio.erp.invoice.dto.InvoiceIssueRequest;
import com.studio.erp.invoice.dto.InvoiceSummaryVO;
import com.studio.erp.invoice.dto.InvoiceVO;
import com.studio.erp.invoice.service.InvoiceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 开票接口。
 *
 * <p>【权限为什么按"申请 / 开票"分开】
 * 申请开票是业务动作（客服、主管都能提）；真正把票开出来涉及税控系统，
 * 只有财务和管理员能做。把两者合成一个权限，等于让客服自己去开票 —— 现实中做不到，
 * 但代码上留下这个口子，日后一定会有人试着绕。
 */
@RestController
@RequestMapping("/api/invoices")
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoiceService invoiceService;

    /** 申请开票（客服 / 主管 / 财务） */
    @PostMapping
    @RequireRole({"ADMIN", "SUPERVISOR", "FINANCE", "CS"})
    public R<Long> apply(@RequestBody InvoiceApplyRequest request) {
        return R.ok(invoiceService.apply(request));
    }

    /** 财务回填票号，标记已开 */
    @PutMapping("/{id}/issue")
    @RequireRole({"ADMIN", "FINANCE"})
    public R<Void> issue(@PathVariable Long id, @RequestBody InvoiceIssueRequest request) {
        invoiceService.issue(id, request);
        return R.ok();
    }

    /** 作废发票（原因写在 body 的 reason 字段） */
    @PutMapping("/{id}/void")
    @RequireRole({"ADMIN", "FINANCE"})
    public R<Void> voidInvoice(@PathVariable Long id, @RequestBody VoidRequest request) {
        invoiceService.voidInvoice(id, request == null ? null : request.reason());
        return R.ok();
    }

    /**
     * 开票台账（分页）。
     *
     * @param status  1待开 2已开 3已作废
     * @param period  申请月份 yyyy-MM
     */
    @GetMapping
    @RequireRole({"ADMIN", "SUPERVISOR", "FINANCE", "CS"})
    public R<PageResult<InvoiceVO>> list(@RequestParam(required = false) Integer status,
                                         @RequestParam(required = false) String period,
                                         @RequestParam(required = false) String keyword,
                                         @RequestParam(defaultValue = "1") int pageNum,
                                         @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(invoiceService.page(status, period, keyword, pageNum, pageSize));
    }

    /** 概览：待开张数/金额、本月已开、累计已开 */
    @GetMapping("/summary")
    @RequireRole({"ADMIN", "SUPERVISOR", "FINANCE", "CS"})
    public R<InvoiceSummaryVO> summary() {
        return R.ok(invoiceService.summary());
    }

    /** 某订单的开票记录（订单详情用） */
    @GetMapping("/orders/{orderId}")
    @RequireRole({"ADMIN", "SUPERVISOR", "FINANCE", "CS"})
    public R<List<InvoiceVO>> byOrder(@PathVariable Long orderId) {
        return R.ok(invoiceService.orderInvoices(orderId));
    }

    /**
     * 作废请求体。
     *
     * <p>用 record 包一层而不是直接收 String：直接收 String 要求请求体是
     * 裸的 JSON 字符串（`"reason"`），前端很容易发成 `{"reason":"..."}`，
     * 然后得到一个看不懂的反序列化错误。
     */
    public record VoidRequest(String reason) {
    }
}
