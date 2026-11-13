package com.studio.erp.common.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 拦截器装配。
 *
 * <p>【分页插件不装会怎样 —— 这是本项目必须写下来的一条】
 * 不注册 {@link PaginationInnerInterceptor} 时，{@code selectPage} 不会报错，
 * 而是**静默地查全表**并把 total 返回成 0。表现是：第一页就返回了 5000 条，
 * 前端以为只有一页，翻页按钮点不动；同时每次请求都把全部员工拉进内存，
 * 数据量一大就 OOM。因为不报错，很多人上线后才发现。
 *
 * <p>【maxLimit 为什么必须设】
 * 分页参数是前端传的。有人（或某个脚本）传 pageSize=100000 就能一次性导出全库，
 * 既是性能风险也是数据外泄通道。这里硬性截断到 200 条 ——
 * 真要导出走专门的导出接口，那里有独立的权限校验和审计日志。
 */
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        pagination.setMaxLimit(200L);
        // 请求页码超过总页数时返回空列表而不是回到第一页。
        // 回到第一页会让前端"下一页"永远刷出同样的数据，看起来像卡死。
        pagination.setOverflow(false);
        interceptor.addInnerInterceptor(pagination);

        /*
         * 【为什么加攻击防护拦截器】
         * 财务系统最怕的不是被黑，而是"手滑"。一句 update 忘了写 where，
         * 整张 performance_ledger 的金额会在一秒内被刷新成同一个值，
         * 而且没有备份就基本救不回来。这个拦截器会直接拒绝没有 where 条件的
         * update / delete，把事故挡在发生之前。
         */
        interceptor.addInnerInterceptor(new BlockAttackInnerInterceptor());

        return interceptor;
    }
}
