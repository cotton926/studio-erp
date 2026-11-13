package com.studio.erp.employee.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 部门。
 *
 * <p>【为什么保留 parent_id 做成树，而当前只用到一层】
 * 设计公司现在就是"设计部/客服部/财务部"平铺，但人一多必然出现
 * "设计一部/设计二部"，或者"制作中心"下面再挂几个组。
 * 预留 parent_id 几乎零成本，等真要拆的时候再改表，就要迁移历史数据。
 *
 * <p>【为什么不做完整的部门增删改】
 * 部门一年可能都不会变一次，做成界面反而多一个误操作入口。
 * 目前的策略是：初始化脚本给好三个部门，需要调整时由管理员执行 SQL。
 * 等真的出现频繁调整诉求，再补接口。
 */
@Data
@TableName("dept")
public class Dept {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    /** 上级部门ID，0 表示顶级 */
    private Long parentId;

    private Long leaderEmpId;

    private Integer sortNo;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
