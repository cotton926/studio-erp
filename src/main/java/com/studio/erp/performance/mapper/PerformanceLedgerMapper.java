package com.studio.erp.performance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.studio.erp.performance.entity.PerformanceLedger;
import org.apache.ibatis.annotations.Mapper;

/**
 * 业绩流水 Mapper。
 *
 * <p>只用 BaseMapper 的通用 insert，不提供 update/delete 方法 ——
 * 从接口层面就杜绝"改流水"，让"只追加"成为不可违反的约束，而不是靠自觉。
 */
@Mapper
public interface PerformanceLedgerMapper extends BaseMapper<PerformanceLedger> {
}
