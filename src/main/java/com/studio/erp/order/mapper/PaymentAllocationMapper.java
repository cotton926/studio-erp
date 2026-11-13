package com.studio.erp.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.studio.erp.order.entity.PaymentAllocation;
import org.apache.ibatis.annotations.Mapper;

/**
 * 回款核销 Mapper。
 *
 * <p>没有额外 SQL：核销记录一旦写入就不再修改（作废走 payment.status），
 * 查询也只需要按订单或按回款单过滤，用 MyBatis-Plus 的 Wrapper 足够。
 */
@Mapper
public interface PaymentAllocationMapper extends BaseMapper<PaymentAllocation> {
}
