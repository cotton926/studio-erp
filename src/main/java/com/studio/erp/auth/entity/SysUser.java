package com.studio.erp.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 登录账号。
 *
 * <p>【为什么账号与员工分表】
 * 离职员工的账号要能停用，但人事记录和它名下的历史业绩必须保留；
 * 另外未来可能给老板开一个只看看板的远程账号，它没有员工档案。
 * 硬塞进 employee 表会让"这个人是不是员工"变成一堆 nullable 判断。
 *
 * <p>【注意这里没有 deleted 字段】
 * 全局配置了逻辑删除字段 deleted，但本表没有该列 —— MyBatis-Plus 只对
 * 实体中真实存在的字段生效，不会凭空拼出 deleted=0 的条件。
 * 账号的"删除"用 status=0 停用表达，历史数据仍可追溯。
 */
@Data
@TableName("sys_user")
public class SysUser {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    /** BCrypt 哈希，长度固定 60，库里留了 100 是给未来换算法留余量 */
    private String passwordHash;

    private Long employeeId;

    /** 1=启用 0=停用 */
    private Integer status;

    /** 1=必须修改密码，改完置 0 */
    private Integer mustChangePwd;

    private LocalDateTime lastLoginAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
