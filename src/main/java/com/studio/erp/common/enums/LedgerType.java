package com.studio.erp.common.enums;

/**
 * 业绩流水类型。
 *
 * <p>【为什么只有这四种】
 * 业绩表是"账"，账只允许记四种动账：登记、确认、冲销、调整。
 * 想表达"改一下金额"？那一定是冲销 + 重新登记，而不是 update。
 * 这个约束让流水永远可追溯，也让月度重算成为可能。
 */
public enum LedgerType {

    /** 签单额：订单确认时一次性生成，记在签单月，用于统计接单量 */
    SIGN(1, "签单额", false),

    /** 业绩确认：每笔回款核销成功时生成，记在到账月，这是员工看的"业绩" */
    CONFIRM(2, "业绩确认", true),

    /** 冲销：废单、退款、撤销核销时生成，金额为负 */
    REVERSE(3, "冲销", true),

    /** 人工调整：老板审批后的调整，必须带理由 */
    ADJUST(4, "人工调整", true);

    private final int code;
    private final String label;

    /** 是否计入「业绩」口径（SIGN 只算签单额，不算业绩） */
    private final boolean countsAsPerformance;

    LedgerType(int code, String label, boolean countsAsPerformance) {
        this.code = code;
        this.label = label;
        this.countsAsPerformance = countsAsPerformance;
    }

    public int getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public boolean countsAsPerformance() {
        return countsAsPerformance;
    }

    public static LedgerType of(int code) {
        for (LedgerType t : values()) {
            if (t.code == code) {
                return t;
            }
        }
        throw new IllegalArgumentException("未知的业绩流水类型: " + code);
    }
}
