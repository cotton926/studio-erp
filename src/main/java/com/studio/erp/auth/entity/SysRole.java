package com.studio.erp.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 角色。
 *
 * <p>权限判断一律用 code 而不是 id：id 是自增的，换个环境重新初始化就可能变，
 * 代码里写死 id==2 迟早会错位成别的角色。
 */
@Data
@TableName("sys_role")
public class SysRole {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** ADMIN / SUPERVISOR / CS / DESIGNER / FINANCE */
    private String code;

    private String name;

    private String remark;
}
