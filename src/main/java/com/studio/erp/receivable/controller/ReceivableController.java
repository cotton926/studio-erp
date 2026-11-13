package com.studio.erp.receivable.controller;

import com.studio.erp.auth.RequireRole;
import com.studio.erp.common.api.R;
import com.studio.erp.receivable.dto.CollectLogVO;
import com.studio.erp.receivable.dto.CollectRequest;
import com.studio.erp.receivable.dto.CollectionSummaryVO;
import com.studio.erp.receivable.dto.CollectionVO;
import com.studio.erp.receivable.dto.PlanBriefVO;
import com.studio.erp.receivable.service.ReceivableService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 收款计划与催收接口。
 *
 * <p>【为什么客服（CS）也能调用】尾款是客服在催 —— 他和客户最熟，
 * 财务只负责到账登记。把催收限定给财务，实际结果是没人催。
 *
 * <p>【为什么没有"修改计划金额"的接口】
 * 计划金额由订单总额推导。允许手工改，就会出现"计划 7000、订单 10000、
 * 客户付清 10000 但计划还剩 3000 未收"这种自相矛盾的数据。
 * 要改金额就改订单（重新定价），不要绕过金额的来源。
 */
@RestController
@RequestMapping("/api/receivables")
@RequiredArgsConstructor
public class ReceivableController {

    private final ReceivableService receivableService;

    /**
     * 催收页顶部概览。
     *
     * <p>返回里带"今天是否在月末催收窗口内"，页面据此决定是否高亮提醒条。
     */
    @GetMapping("/summary")
    @RequireRole({"ADMIN", "SUPERVISOR", "FINANCE", "CS"})
    public R<CollectionSummaryVO> summary() {
        return R.ok(receivableService.summary());
    }

    /**
     * 催收清单。
     *
     * @param scope   WINDOW=本期窗口（月末最后三天到期）/ OVERDUE=已逾期 / ALL=全部未结清
     * @param keyword 订单号 / 客户 / 项目
     */
    @GetMapping("/collection")
    @RequireRole({"ADMIN", "SUPERVISOR", "FINANCE", "CS"})
    public R<List<CollectionVO>> collection(@RequestParam(required = false) String scope,
                                            @RequestParam(required = false) String keyword) {
        return R.ok(receivableService.collection(scope, keyword));
    }

    /** 某张订单的收款计划（订单详情用，任何能看金额的角色都可以看） */
    @GetMapping("/orders/{orderId}/plans")
    @RequireRole({"ADMIN", "SUPERVISOR", "FINANCE", "CS"})
    public R<List<PlanBriefVO>> plans(@PathVariable Long orderId) {
        return R.ok(receivableService.plans(orderId));
    }

    /** 登记一次催收（方式 + 结果 + 客户承诺） */
    @PostMapping("/{planId}/collect")
    @RequireRole({"ADMIN", "SUPERVISOR", "FINANCE", "CS"})
    public R<Long> collect(@PathVariable Long planId, @RequestBody CollectRequest request) {
        return R.ok(receivableService.collect(planId, request));
    }

    /** 某张订单的催收历史（倒序，最近一次在最上面） */
    @GetMapping("/orders/{orderId}/logs")
    @RequireRole({"ADMIN", "SUPERVISOR", "FINANCE", "CS"})
    public R<List<CollectLogVO>> logs(@PathVariable Long orderId) {
        return R.ok(receivableService.logs(orderId));
    }

    /**
     * 为历史订单补建收款计划。
     *
     * <p>只有管理员能点：它会批量写库，且改的是财务口径的数据。
     */
    @PostMapping("/rebuild")
    @RequireRole({"ADMIN"})
    public R<Integer> rebuild() {
        return R.ok(receivableService.rebuild());
    }
}
