package com.studio.erp.employee.controller;

import com.studio.erp.auth.RequireRole;
import com.studio.erp.common.api.PageResult;
import com.studio.erp.common.api.R;
import com.studio.erp.employee.dto.DeptVO;
import com.studio.erp.employee.dto.EmployeeOptionVO;
import com.studio.erp.employee.dto.EmployeeQuery;
import com.studio.erp.employee.dto.EmployeeSaveRequest;
import com.studio.erp.employee.dto.EmployeeVO;
import com.studio.erp.employee.service.EmployeeService;
import jakarta.validation.Valid;
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
 * 员工档案接口。
 *
 * <p>【为什么用 {@code @RequireRole} 而不是 {@code @DataScope(SELF)} 之类】
 * 员工列表里带着手机号、岗位、账号状态，属于人事信息。
 * 数据范围只能表达"看多少行"，管不住"这一行里有哪些列"：
 * 客服的 ScopeType 也是 TEAM，如果用范围控制，客服就能看到全公司员工的手机号。
 * 所以这里按角色卡死：只有管理员和主管能进。
 *
 * <p>【主管为什么能看全公司员工】
 * 派单要选人，主管必须知道有哪些设计师、谁在职。但他看不到账号相关的操作按钮
 * （前端按角色隐藏），服务端的写接口也全部只给 ADMIN。
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class EmployeeController {

    private final EmployeeService employeeService;

    /** 员工列表，支持关键词 / 部门 / 岗位 / 在职状态 / 是否有账号 筛选 */
    @GetMapping("/employees")
    @RequireRole({"ADMIN", "SUPERVISOR"})
    public R<PageResult<EmployeeVO>> page(EmployeeQuery query) {
        return R.ok(employeeService.page(query));
    }

    @GetMapping("/employees/{id}")
    @RequireRole({"ADMIN", "SUPERVISOR"})
    public R<EmployeeVO> detail(@PathVariable Long id) {
        return R.ok(employeeService.detail(id));
    }

    /** 新增员工档案（不建账号，账号走 /accounts） */
    @PostMapping("/employees")
    @RequireRole("ADMIN")
    public R<EmployeeVO> create(@Valid @RequestBody EmployeeSaveRequest request) {
        return R.ok(employeeService.create(request));
    }

    @PutMapping("/employees/{id}")
    @RequireRole("ADMIN")
    public R<EmployeeVO> update(@PathVariable Long id, @Valid @RequestBody EmployeeSaveRequest request) {
        return R.ok(employeeService.update(id, request));
    }

    /**
     * 在职 / 离职。
     *
     * <p>用 PUT + 查询参数而不是请求体，是因为这是一个幂等的状态赋值：
     * 重复调用十次结果一样，前端不需要关心"当前是什么状态"。
     */
    @PutMapping("/employees/{id}/status")
    @RequireRole("ADMIN")
    public R<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        employeeService.updateStatus(id, status);
        return R.ok();
    }

    /** 派单下拉：只返回在职且参与业绩分成的人 */
    @GetMapping("/employees/options")
    @RequireRole({"ADMIN", "SUPERVISOR"})
    public R<List<EmployeeOptionVO>> options(@RequestParam(required = false) Long deptId) {
        return R.ok(employeeService.designerOptions(deptId));
    }

    /**
     * 部门下拉。
     * 只要求登录：录单、筛人、报表都要用它，逐角色配置只会漏。
     * 这里返回的只有部门名称和排序号，不含负责人等敏感信息。
     */
    @GetMapping("/depts")
    public R<List<DeptVO>> depts() {
        return R.ok(employeeService.depts());
    }
}
