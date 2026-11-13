package com.studio.erp.salary.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.studio.erp.salary.entity.SalaryBasePolicy;
import org.apache.ibatis.annotations.Mapper;

/**
 * 分级底薪政策 Mapper。
 *
 * <p>政策只有几行，全量取出即可，不做条件查询 —— 少一条 SQL 就少一处口径。
 *
 * <p>【为什么这里没有 deleteAll】
 * 项目里注册了 BlockAttackInnerInterceptor，它会拦截任何"没有 where 条件的
 * delete/update"，把 {@code DELETE FROM salary_base_policy} 直接判成
 * "Prohibition of full table deletion" 抛异常 —— 试过一次，接口 500。
 * 与其绕开保护，不如改成按业务键同步（见 {@code SalaryService#replaceBasePolicy}）：
 * 该改的 update、该加的 insert、该删的按 id 删。副作用是没有这条 SQL 了，
 * 但也顺带保住了已有行 id 不变。
 */
@Mapper
public interface SalaryBasePolicyMapper extends BaseMapper<SalaryBasePolicy> {
}
