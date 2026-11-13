package com.studio.erp.invoice;

import com.studio.erp.invoice.domain.InvoiceStatus;
import com.studio.erp.invoice.domain.InvoiceType;
import com.studio.erp.invoice.domain.TaxSplit;
import com.studio.erp.receivable.domain.PlanStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 开票与收款计划的纯计算规则测试。
 *
 * <p>【测的是"钱怎么拆"和"状态怎么推"】这两件事一旦算错，
 * 表现是发票金额对不上税控系统、催收清单催错期次，而且都不会抛异常 ——
 * 只能靠单元测试把它钉住。
 */
@DisplayName("开票金额与计划状态")
class InvoiceAmountTest {

    @Test
    @DisplayName("6% 价内税：10000 含税 → 不含税 9433.96，税额 566.04")
    void taxSplitSixPercent() {
        TaxSplit s = TaxSplit.of(new BigDecimal("10000.00"), new BigDecimal("0.0600"));
        assertEquals(new BigDecimal("9433.96"), s.net());
        assertEquals(new BigDecimal("566.04"), s.tax());
        // 拆完必须能拼回去，差 1 分都会让报表两端对不上
        assertEquals(s.gross(), s.net().add(s.tax()));
    }

    @Test
    @DisplayName("无税率时全额视为不含税收入")
    void taxSplitZero() {
        TaxSplit s = TaxSplit.of(new BigDecimal("3000.00"), BigDecimal.ZERO);
        assertEquals(new BigDecimal("3000.00"), s.net());
        assertEquals(0, s.tax().signum());
    }

    @Test
    @DisplayName("13% 税率（销售货物）也要能正确拆分")
    void taxSplitThirteen() {
        TaxSplit s = TaxSplit.of(new BigDecimal("1130.00"), new BigDecimal("0.1300"));
        assertEquals(new BigDecimal("1000.00"), s.net());
        assertEquals(new BigDecimal("130.00"), s.tax());
    }

    @Test
    @DisplayName("计划状态由金额推导：0 未收、部分 部分收、收满 已收讫")
    void planStatusDerive() {
        assertEquals(PlanStatus.OPEN, PlanStatus.derive(new BigDecimal("0.00"), new BigDecimal("7000.00")));
        assertEquals(PlanStatus.PARTIAL, PlanStatus.derive(new BigDecimal("3000.00"), new BigDecimal("7000.00")));
        assertEquals(PlanStatus.PAID, PlanStatus.derive(new BigDecimal("7000.00"), new BigDecimal("7000.00")));
        // 多收（客户付多了）也算已收讫，不能因为超出就回到"部分收"
        assertEquals(PlanStatus.PAID, PlanStatus.derive(new BigDecimal("8000.00"), new BigDecimal("7000.00")));
    }

    @Test
    @DisplayName("发票类型与状态的码值映射必须稳定（前端按码值上色）")
    void codeMapping() {
        assertEquals("专用发票", InvoiceType.labelOf(2));
        assertEquals("普通发票", InvoiceType.labelOf(null));
        assertEquals("待开", InvoiceStatus.labelOf(1));
        assertEquals("已作废", InvoiceStatus.labelOf(3));
        // 脏数据不能抛异常把整个列表打挂
        assertEquals("未知", InvoiceStatus.labelOf(99));
    }
}
