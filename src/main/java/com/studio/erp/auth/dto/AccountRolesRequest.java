package com.studio.erp.auth.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 调整账号角色。
 *
 * <p>【为什么必须整体覆盖而不是增删单个角色】
 * 前端就是一组复选框，用户勾完提交的是"最终状态"。如果做成 add/remove 两个接口，
 * 并发点两下或网络重试就会重复插入，或者删掉刚加上的角色。
 * 整体覆盖是幂等的：同样的请求发十次，结果一样。
 */
@Data
public class AccountRolesRequest {

    @NotEmpty(message = "至少选择一个角色，否则账号登录后什么都看不到")
    private List<String> roleCodes;
}
