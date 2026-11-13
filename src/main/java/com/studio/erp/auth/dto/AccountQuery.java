package com.studio.erp.auth.dto;

import lombok.Data;

/**
 * 账号列表查询条件。
 *
 * <p>刻意不继承员工查询条件：两者的筛选维度完全不同
 * （账号按角色筛，员工按部门/岗位筛），共用父类会带上一堆用不到的条件，
 * 而且以后员工查询加字段会污染账号查询。
 */
@Data
public class AccountQuery {

    private long pageNum = 1;

    private long pageSize = 20;

    /** 登录名 / 员工姓名 / 工号 模糊匹配 */
    private String keyword;

    /** 1=启用 0=停用 */
    private Integer status;

    /** 按角色码筛选，如 DESIGNER */
    private String roleCode;
}
