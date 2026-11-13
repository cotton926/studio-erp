package com.studio.erp.employee.dto;

import lombok.Data;

/**
 * 派单下拉选项。
 *
 * <p>【为什么不直接复用 EmployeeVO】
 * 派单下拉会被前端在每个订单里调用一次，返回一个包含手机号、入职日期、
 * 最后登录时间的完整对象，既浪费带宽也把不该给设计师看的字段送了出去。
 * 下拉框只需要"能选谁"，越窄越安全。
 *
 * <p>只返回在职且 is_designer=1 的人。为什么用 is_designer 而不是 position：
 * 主管自己出图也要能被派单，靠岗位字符串过滤会把他排除掉。
 */
@Data
public class EmployeeOptionVO {

    private Long id;

    private String name;

    private String empNo;

    private String deptName;

    private String position;
}
