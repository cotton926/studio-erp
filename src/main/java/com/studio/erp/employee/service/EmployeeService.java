package com.studio.erp.employee.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.studio.erp.auth.CurrentUserHolder;
import com.studio.erp.auth.service.AccountService;
import com.studio.erp.common.api.PageResult;
import com.studio.erp.common.exception.BizException;
import com.studio.erp.common.exception.ErrorCode;
import com.studio.erp.employee.dto.DeptVO;
import com.studio.erp.employee.dto.EmployeeOptionVO;
import com.studio.erp.employee.dto.EmployeeQuery;
import com.studio.erp.employee.dto.EmployeeSaveRequest;
import com.studio.erp.employee.dto.EmployeeVO;
import com.studio.erp.employee.domain.EmpLevel;
import com.studio.erp.employee.entity.Dept;
import com.studio.erp.employee.entity.Employee;
import com.studio.erp.employee.mapper.DeptMapper;
import com.studio.erp.employee.mapper.EmployeeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/**
 * 员工档案。
 *
 * <p>【这里没有 delete 方法，是刻意的】
 * 员工的实际删除会把他名下的业绩流水、订单派单记录全部变成孤儿数据：
 * 报表里这些业绩会归到"无人"，对账永远差一块，而且无法复原。
 * 离职一律用 status=0，历史数据完整保留。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeeService {

    /**
     * 允许的岗位。
     * 白名单而不是自由文本：岗位会参与"默认角色推导"和"派单过滤"，
     * 一旦有人录成"设计师 "（尾部空格）或"designer"（小写），
     * 这个人就会从派单列表里消失，而系统不会报任何错。
     *
     * <p>用 List 而不是 Set 只为了一件事：报错提示里的顺序稳定。
     * HashSet 的遍历顺序取决于哈希值，同一份错误提示每次刷新顺序都可能不同，
     * 用户截图反馈时看起来像两个不同的问题。
     */
    private static final List<String> POSITIONS =
            List.of("DESIGNER", "CS", "SUPERVISOR", "FINANCE", "ADMIN");

    private final EmployeeMapper employeeMapper;
    private final DeptMapper deptMapper;
    private final AccountService accountService;

    /** 员工分页列表（含账号开立情况） */
    public PageResult<EmployeeVO> page(EmployeeQuery query) {
        Page<EmployeeVO> page = new Page<>(query.getPageNum(), query.getPageSize());
        IPage<EmployeeVO> result = employeeMapper.selectEmployeePage(page, query);
        return PageResult.of(result.getCurrent(), result.getSize(), result.getTotal(), result.getRecords());
    }

    public EmployeeVO detail(Long id) {
        EmployeeVO vo = employeeMapper.selectEmployeeDetail(id);
        if (vo == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "员工不存在");
        }
        return vo;
    }

    /**
     * 新增员工。
     *
     * <p>新增只建人事档案，不建登录账号 —— 这两件事的时点经常不一样：
     * 人还没到岗就先录档案，或者兼职人员只需要档案不需要账号。
     * 账号由账号管理单独开，避免"录个档案就自动多出一个能登录的账号"。
     */
    @Transactional(rollbackFor = Exception.class)
    public EmployeeVO create(EmployeeSaveRequest request) {
        String position = normalizePosition(request.getPosition());
        assertEmpNoAvailable(request.getEmpNo(), null);

        Employee employee = new Employee();
        employee.setEmpNo(request.getEmpNo().trim());
        employee.setName(request.getName().trim());
        employee.setDeptId(request.getDeptId());
        employee.setPosition(position);
        employee.setEmpLevel(resolveEmpLevel(position, request.getEmpLevel()).name());
        employee.setIsDesigner(resolveIsDesigner(position, request.getIsDesigner()));
        employee.setPhone(request.getPhone());
        employee.setHireDate(request.getHireDate());
        employee.setStatus(1);

        employeeMapper.insert(employee);
        log.info("新增员工 id={} empNo={} name={} position={} empLevel={} isDesigner={}",
                employee.getId(), employee.getEmpNo(), employee.getName(),
                position, employee.getEmpLevel(), employee.getIsDesigner());
        return detail(employee.getId());
    }

    /**
     * 修改员工档案。
     *
     * <p>【为什么用 UpdateWrapper 逐列 set，而不是 updateById】
     * updateById 默认忽略 null 字段，于是"把手机号清空"这个操作永远存不进去 ——
     * 用户删了号码点保存，提示成功，刷新后又回来了。
     * 逐列 set 才能表达"这一列就是要变成空"。
     */
    @Transactional(rollbackFor = Exception.class)
    public EmployeeVO update(Long id, EmployeeSaveRequest request) {
        Employee existing = employeeMapper.selectById(id);
        if (existing == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "员工不存在");
        }
        String position = normalizePosition(request.getPosition());
        assertEmpNoAvailable(request.getEmpNo(), id);

        employeeMapper.update(null, Wrappers.<Employee>lambdaUpdate()
                .eq(Employee::getId, id)
                .set(Employee::getEmpNo, request.getEmpNo().trim())
                .set(Employee::getName, request.getName().trim())
                .set(Employee::getDeptId, request.getDeptId())
                .set(Employee::getPosition, position)
                .set(Employee::getEmpLevel, resolveEmpLevel(position, request.getEmpLevel()).name())
                .set(Employee::getIsDesigner, resolveIsDesigner(position, request.getIsDesigner()))
                .set(Employee::getPhone, request.getPhone())
                .set(Employee::getHireDate, request.getHireDate()));

        log.info("修改员工 id={} empNo={}", id, request.getEmpNo());
        return detail(id);
    }

    /**
     * 在职 / 离职切换。
     *
     * <p>【离职为什么一定要联动停用账号】
     * 见 {@link AccountService#disableByEmployeeId}：不改账号，前员工能一直登录，
     * 而这件事没人会主动想起来做。
     *
     * <p>【复职为什么不一并启用账号】
     * 复职不等于恢复原权限。有人离职是被调去别的部门、有人是停薪留职，
     * 直接恢复登录权限属于"悄悄给了一个入口"。这里选择让管理员显式再启用一次，
     * 顺便重新确认一遍角色。
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "状态只能是 0（离职）或 1（在职）");
        }
        Employee existing = employeeMapper.selectById(id);
        if (existing == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "员工不存在");
        }
        if (status == 0) {
            Long currentEmployeeId = CurrentUserHolder.get() == null
                    ? null : CurrentUserHolder.get().employeeId();
            if (id.equals(currentEmployeeId)) {
                throw new BizException(ErrorCode.CANNOT_OPERATE_SELF, "不能把自己标记为离职");
            }
        }
        if (existing.getStatus() != null && existing.getStatus() == status) {
            return;
        }

        Employee update = new Employee();
        update.setId(id);
        update.setStatus(status);
        employeeMapper.updateById(update);

        if (status == 0) {
            accountService.disableByEmployeeId(id);
        }
        log.info("员工状态变更 id={} empNo={} status={}", id, existing.getEmpNo(), status);
    }

    /** 派单下拉：在职且参与业绩分成的人 */
    public List<EmployeeOptionVO> designerOptions(Long deptId) {
        return employeeMapper.selectDesignerOptions(deptId);
    }

    /** 部门列表（下拉框） */
    public List<DeptVO> depts() {
        List<Dept> list = deptMapper.selectList(
                Wrappers.<Dept>lambdaQuery().orderByAsc(Dept::getSortNo).orderByAsc(Dept::getId));
        return list.stream()
                .map(d -> new DeptVO(d.getId(), d.getName(), d.getParentId(), d.getSortNo()))
                .toList();
    }

    /* ------------------------------------------------------------------ */

    private String normalizePosition(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BizException(ErrorCode.POSITION_INVALID);
        }
        String position = raw.trim().toUpperCase(Locale.ROOT);
        if (!POSITIONS.contains(position)) {
            throw new BizException(ErrorCode.POSITION_INVALID, "岗位只能是 " + POSITIONS);
        }
        return position;
    }

    /**
     * 员工级别。
     *
     * <p>不传时按岗位推断（主管/管理员 → 主管档，其余 → 普通员工），
     * 传了就必须是白名单里的值。级别直接决定底薪金额，
     * 允许"随便传个字符串然后静默存进去"，等于让底薪变成不可预测的值。
     */
    private EmpLevel resolveEmpLevel(String position, String requested) {
        if (requested == null || requested.isBlank()) {
            return EmpLevel.fromPosition(position);
        }
        try {
            return EmpLevel.parse(requested);
        } catch (IllegalArgumentException e) {
            throw new BizException(ErrorCode.EMP_LEVEL_INVALID, e.getMessage());
        }
    }

    /**
     * 是否参与业绩分成。
     *
     * <p>不传时按岗位推断：设计师默认参与，其他岗位默认不参与。
     * 允许显式覆盖，是因为"主管自己出图""客服兼做设计"都真实存在，
     * 靠岗位一刀切会让这些人做的单子没人可分。
     */
    private int resolveIsDesigner(String position, Integer requested) {
        if (requested != null) {
            return requested == 1 ? 1 : 0;
        }
        return "DESIGNER".equals(position) ? 1 : 0;
    }

    /**
     * 工号唯一性校验。
     *
     * @param excludeId 修改时排除自己，否则"只改了姓名"也会被判成工号重复
     */
    private void assertEmpNoAvailable(String empNo, Long excludeId) {
        if (empNo == null || empNo.isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "工号不能为空");
        }
        Long count = employeeMapper.selectCount(Wrappers.<Employee>lambdaQuery()
                .eq(Employee::getEmpNo, empNo.trim())
                .ne(excludeId != null, Employee::getId, excludeId));
        if (count != null && count > 0) {
            throw new BizException(ErrorCode.EMP_NO_DUPLICATED);
        }
    }
}
