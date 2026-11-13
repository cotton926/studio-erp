package com.studio.erp.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明接口所需的最低数据可见范围。
 *
 * <p>【为什么用注解而不是在 Service 里写 if】
 * "谁能看全部、谁只能看自己"这条规则，如果散落在 20 个方法里各写一遍，
 * 只要漏写一个，就是一个数据泄漏。注解写在方法上，
 * 由切面统一拦截，漏写会表现成"403 打不开"（明显），
 * 而不是"数据被别人看到了"（隐蔽且无法挽回）。
 *
 * <p>【它只管"能不能进这个接口"】
 * 进来之后"能看到哪些行"还要靠 SQL 条件，两者缺一不可：
 * 注解是门锁，SQL 条件是把东西锁在抽屉里。
 * 只有门锁没抽屉，一旦有人绕过接口直接调 Service 就露了；
 * 只有抽屉没门锁，接口本身就成了数据导出器。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface DataScope {

    /** 所需最低数据范围，默认仅本人 */
    ScopeType value() default ScopeType.SELF;
}
