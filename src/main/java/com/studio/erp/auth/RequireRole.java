package com.studio.erp.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明接口要求哪些角色。
 *
 * <p>【为什么有了 {@link DataScope} 还需要这个】
 * 两者回答的是不同问题：
 * · DataScope 回答"能看多大范围的数据"（自己 / 团队 / 全部）；
 * · RequireRole 回答"这个动作归不归你干"（能不能建账号、能不能改价目表）。
 * 只看范围会出问题：客服和主管的 ScopeType 都是 TEAM，
 * 但"给员工开账号"绝不能因为客服也属于 TEAM 就放行。
 *
 * <p>【为什么默认是"任一匹配"】
 * 实际操作里绝大多数接口是"主管或管理员都行"，写起来最自然；
 * 需要"必须同时具备两个角色"的极少（本项目目前没有），
 * 真有这种需求时把 requireAll 打开即可。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireRole {

    /** 允许的角色码，如 {"ADMIN", "SUPERVISOR"} */
    String[] value();

    /** true = 必须同时具备全部角色；false（默认）= 具备任一即可 */
    boolean requireAll() default false;
}
