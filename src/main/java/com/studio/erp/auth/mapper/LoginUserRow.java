package com.studio.erp.auth.mapper;

import lombok.Data;

import java.io.Serializable;

/**
 * 登录/鉴权时一次性取出的账号视图（账号 + 员工姓名）。
 *
 * <p>【为什么要联表取，而不是先查账号再查员工】
 * 每个请求都要构建一次 CurrentUser，两次往返就是两倍延迟。
 * 内部系统虽然只有几十号人，但业绩页会一次触发多个接口，
 * 能省一次查询就省一次。
 *
 * <p>【passwordHash 只在校验密码时使用】
 * 这个对象不会直接作为接口返回值，转成 VO 时必须显式挑字段，
 * 避免哪天顺手 return 出去把哈希泄露给前端。
 */
@Data
public class LoginUserRow implements Serializable {

    private Long id;

    private String username;

    private String passwordHash;

    private Long employeeId;

    /** 员工姓名，纯系统账号可能为空 */
    private String employeeName;

    /** 1=启用 0=停用 */
    private Integer status;

    /** 1=必须修改密码 */
    private Integer mustChangePwd;
}
