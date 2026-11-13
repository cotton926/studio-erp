package com.studio.erp.performance.domain;

/**
 * 一条明细行上的一个分成人。
 *
 * <p>【为什么比例用 int 万分比而不是 BigDecimal / double】
 * · 为什么：比例要做乘法并参与"合计必须等于 100%"的判断，小数永远判不准。
 * · 作用：7000 表示 70%，10000 表示 100%，整数相加 4200+5800 一定等于 10000。
 * · 问题：用 double 存 0.7、0.2、0.1，三者相加是 0.9999999999999999，校验失败；
 *         用 BigDecimal 存 33.33%，三份相加 99.99%，要么放宽校验（比例可超 100%），
 *         要么逼着主管手工凑最后一份（体验极差）。
 * · 解决：录入层收百分比字符串（"70"），转换层 ×100 得 7000；展示层 ÷100 还原。
 *         转换负责到底，业务代码里不会再看到小数比例。
 *
 * @param employeeId 员工ID
 * @param ratioBp    分成比例，万分比
 */
public record AssigneeShare(Long employeeId, int ratioBp) {

    /**
     * 紧凑构造器做参数校验。
     *
     * <p>【为什么在 record 里校验】
     * 一旦对象构造成功就代表"数据合法"，后面所有计算都可以省掉防御性判断。
     * 校验放在最上游，脏数据就进不了系统。
     */
    public AssigneeShare {
        if (employeeId == null) {
            throw new IllegalArgumentException("分成人不能为空：明细行必须指定至少一名设计师");
        }
        if (ratioBp < 0 || ratioBp > RatioUtil.FULL_BP) {
            throw new IllegalArgumentException(
                    "分成比例必须在 0~" + RatioUtil.FULL_BP + "bp 之间，实际传入 " + ratioBp);
        }
    }

    /** 便捷构造：用百分比整数创建，如 70 → 7000bp */
    public static AssigneeShare ofPercent(Long employeeId, int percent) {
        return new AssigneeShare(employeeId, percent * 100);
    }
}
