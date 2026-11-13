package com.studio.erp.salary.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.studio.erp.salary.entity.SalaryTier;
import org.apache.ibatis.annotations.Mapper;

/**
 * 工资阶梯 Mapper。
 *
 * <p>没有额外查询 SQL：档位只有几行，按 min_amount 排序全量取出即可，
 * 不需要分页也不需要条件查询 —— 多写一条 SQL 就多一处要维护的口径。
 *
 * <p>【为什么原来那条 deleteAll 被删掉了】
 * 原实现是"先 DELETE FROM salary_tier 再逐条插入"。但项目注册了
 * BlockAttackInnerInterceptor，它把没有 where 的 delete 判成
 * "Prohibition of full table deletion" 直接抛异常 —— 也就是说
 * 主管一点"保存阶梯"就会 500，只是此前没测过这条路径。
 * 现在改成按 min_amount 同步（该改的 update、该加的 insert、该删的按 id 删），
 * 既绕开了全表操作，也不会在替换瞬间把档位清空成 0 行。
 */
@Mapper
public interface SalaryTierMapper extends BaseMapper<SalaryTier> {
}
