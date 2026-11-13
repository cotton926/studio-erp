package com.studio.erp.performance.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 「我的业绩」页面数据。
 *
 * <p>【为什么一个接口给全页，而不是拆成 5 个接口】
 * 这页的 4 个 KPI + 趋势 + 明细必须来自同一时刻的数据。拆成多个请求后，
 * 主管刚好在两次请求之间确认了一笔回款，就会出现"本月业绩 18,550 但明细加起来 21,000"
 * —— 员工会截图来问，而客服无法解释。一次查询、一次组装，数字天然自洽。
 *
 * <p>【为什么明细里没有订单总额与客户实收】
 * 设计师只看自己那部分。订单总额、客户付了多少属于公司经营数据，
 * 一旦返回给前端，哪怕页面不显示，打开控制台也能看到。
 * 这里只给 {@code mySignAmount} / {@code myPaidAmount} / {@code myUnpaidAmount}，
 * 都是由"我的分成"算出来的，凑不出整单金额。
 */
@Data
public class PerfSummaryVO {

    private String period;

    private Long employeeId;

    private String employeeName;

    /** 本月业绩：本月到账回款按分成确认的金额（含冲销/调整） */
    private BigDecimal performance;

    /** 上月业绩，用于算环比 */
    private BigDecimal lastMonthPerformance;

    /** 环比百分比，如 50.8 表示 +50.8%；上月为 0 时为 null（除零无意义） */
    private BigDecimal momRate;

    /** 本年累计业绩 */
    private BigDecimal yearPerformance;

    /** 本月参与的单数 */
    private Integer orderCount;

    /** 未收业绩：我参与过且尚未结清的订单，按分成折算后的未收部分 */
    private BigDecimal unpaidAmount;

    /** 本月签单额（仅展示，不参与提成） */
    private BigDecimal signAmount;

    /** 近 12 个月业绩，按时间升序 */
    private List<TrendPoint> trend;

    /** 本月订单明细（订单粒度） */
    private List<MyOrder> orders;

    /**
     * 趋势上的一个点。
     *
     * @param label 图表横轴文案，如 "11月"；跨年时前台靠 period 判断
     * @param current 是否当前所选月份，用于高亮
     */
    public record TrendPoint(String period, String label, BigDecimal amount, boolean current) {
    }

    /**
     * 我参与的一张订单。
     *
     * @param ratioText    我的分成，如 "70%"；多行比例不同时给"混合"
     * @param mySignAmount 我的签单额 = Σ(明细金额 × 我的比例)
     * @param myPaidAmount 我的已收 = 我的签单额 × (整单已收 / 整单金额)
     * @param myUnpaid     mySignAmount - myPaidAmount
     */
    public record MyOrder(
            Long orderId,
            String orderNo,
            String clientName,
            String title,
            String itemNames,
            LocalDate signDate,
            String statusText,
            Integer statusCode,
            String paymentStatusText,
            String ratioText,
            BigDecimal mySignAmount,
            BigDecimal myPaidAmount,
            BigDecimal myUnpaid) {
    }
}
