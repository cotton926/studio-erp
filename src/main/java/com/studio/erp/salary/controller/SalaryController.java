package com.studio.erp.salary.controller;

import com.studio.erp.auth.DataScope;
import com.studio.erp.auth.RequireRole;
import com.studio.erp.auth.ScopeType;
import com.studio.erp.common.api.R;
import com.studio.erp.salary.dto.BasePolicySaveRequest;
import com.studio.erp.salary.dto.BasePolicyVO;
import com.studio.erp.salary.dto.BaseSalaryRequest;
import com.studio.erp.salary.dto.SalaryVO;
import com.studio.erp.salary.dto.TierSaveRequest;
import com.studio.erp.salary.dto.TierVO;
import com.studio.erp.salary.service.SalaryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 工资与提成阶梯接口。
 *
 * <p>【工资页的权限为什么比业绩页更严】
 * 业绩是"你做了多少"，工资是"公司给你多少钱"。前者同事之间私下会比，
 * 后者一旦全员可见就是人事事故。所以：
 * · 员工只能看自己（{@code /my} 不接收 employeeId，没有越权入口）；
 * · 主管和老板才能看团队表；
 * · 改阶梯只有管理员能做 —— 改一个数字就能让全公司工资变，必须是单人负责。
 */
@RestController
@RequestMapping("/api/salary")
@RequiredArgsConstructor
public class SalaryController {

    private final SalaryService salaryService;

    /**
     * 我的工资单。
     *
     * @param period 期间 yyyy-MM，不传默认当月
     */
    @GetMapping("/my")
    @DataScope(ScopeType.SELF)
    public R<SalaryVO> my(@RequestParam(required = false) String period) {
        return R.ok(salaryService.mySalary(period));
    }

    /**
     * 团队工资表（主管 / 老板）。
     *
     * @param keyword 按姓名或工号筛选
     */
    @GetMapping("/team")
    @DataScope(ScopeType.TEAM)
    @RequireRole({"ADMIN", "SUPERVISOR"})
    public R<List<SalaryVO>> team(@RequestParam(required = false) String period,
                                  @RequestParam(required = false) String keyword) {
        return R.ok(salaryService.teamSalary(period, keyword));
    }

    /** 档位列表：员工也能看，用于解释"为什么我这个月是 45%" */
    @GetMapping("/tiers")
    @DataScope(ScopeType.SELF)
    public R<List<TierVO>> tiers() {
        return R.ok(salaryService.listTiers());
    }

    /**
     * 底薪政策：普通 / 精英 / 主管 各是多少。
     *
     * <p>全员只读。员工要能核对"我的 2000 是公司标准还是主管随口定的"。
     */
    @GetMapping("/base-policy")
    @DataScope(ScopeType.SELF)
    public R<List<BasePolicyVO>> basePolicy() {
        return R.ok(salaryService.listBasePolicy());
    }

    /**
     * 整表替换底薪政策。
     *
     * <p>只有管理员能改：一个数字下去，全公司底薪都变。
     */
    @PutMapping("/base-policy")
    @RequireRole({"ADMIN"})
    public R<List<BasePolicyVO>> saveBasePolicy(@Valid @RequestBody BasePolicySaveRequest request) {
        return R.ok(salaryService.replaceBasePolicy(request));
    }

    /** 整表替换档位 */
    @PutMapping("/tiers")
    @RequireRole({"ADMIN"})
    public R<List<TierVO>> saveTiers(@Valid @RequestBody TierSaveRequest request) {
        return R.ok(salaryService.replaceTiers(request));
    }

    /** 调整某人底薪；baseSalary 传 null 表示撤销特例、恢复按级别 */
    @PutMapping("/base/{employeeId}")
    @RequireRole({"ADMIN", "SUPERVISOR"})
    public R<Void> updateBaseSalary(@PathVariable Long employeeId,
                                    @RequestBody BaseSalaryRequest request) {
        salaryService.updateBaseSalary(employeeId, request == null ? null : request.baseSalary());
        return R.ok();
    }
}
