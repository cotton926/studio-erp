package com.studio.erp.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.studio.erp.order.entity.Payment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 回款单 Mapper。
 */
@Mapper
public interface PaymentMapper extends BaseMapper<Payment> {

    /**
     * 取本月最后一个回款单号并加锁。
     *
     * <p>理由同 {@link SalesOrderMapper#selectLastOrderNo}：
     * 先查最大值再 +1 在并发下必撞号，靠行锁串行化，
     * 再靠唯一索引 uk_payment_no 做最后兜底。
     */
    @Select("""
            SELECT payment_no
              FROM payment
             WHERE payment_no LIKE CONCAT(#{prefix}, '%')
             ORDER BY payment_no DESC
             LIMIT 1
             FOR UPDATE
            """)
    String selectLastPaymentNo(@Param("prefix") String prefix);
}
