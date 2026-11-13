package com.studio.erp.performance.controller;

import com.studio.erp.common.api.R;
import com.studio.erp.performance.dto.PerfSummaryVO;
import com.studio.erp.performance.entity.PerformanceLedger;
import com.studio.erp.performance.service.PerformanceQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 业绩查询接口。
 *
 * <p>【这个 Controller 的方法里看不到任何权限判断，这是故意的】
 * 隔离逻辑在 {@code PerformanceQueryService} 上：注解管"能不能进"，
 * DataScopeGuard 管"能看谁的"。控制器只做参数搬运，
 * 这样即使以后再加十个入口，也不会出现"某个入口忘了过滤"。
 *
 * <p>【测试要点】用设计师账号传 employeeId=别人 的 id，返回的仍应是自己 的数据。
 */
@RestController
@RequestMapping("/api/performance")
@RequiredArgsConstructor
public class PerformanceController {

    private final PerformanceQueryService queryService;

    /**
     * 查业绩流水。
     *
     * @param employeeId 目标员工，可不传（查自己）；低权限用户传了别人的也会被改写
     * @param period     期间 yyyy-MM，可不传
     */
    @GetMapping("/ledger")
    public R<List<PerformanceLedger>> ledger(
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) String period) {
        return R.ok(queryService.listLedger(employeeId, period));
    }

    /**
     * 「我的业绩」页面数据：KPI + 近 12 月趋势 + 本月订单明细。
     *
     * <p>不接收 employeeId —— 这页永远是"我的"。要看别人的走团队报表，
     * 那条路径有独立的权限校验。少一个参数就少一条越权路径。
     */
    @GetMapping("/summary")
    public R<PerfSummaryVO> summary(@RequestParam(required = false) String period) {
        return R.ok(queryService.summary(period));
    }
}
