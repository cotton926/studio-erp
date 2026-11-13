package com.studio.erp.common.audit;

import com.studio.erp.auth.CurrentUser;
import com.studio.erp.auth.CurrentUserHolder;
import com.studio.erp.common.audit.entity.AuditLog;
import com.studio.erp.common.audit.mapper.AuditLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 写审计日志。
 *
 * <p>【为什么和被审计的业务用同一个事务，而不是 REQUIRES_NEW】
 * 两种选择各有代价：
 * <ul>
 *   <li>独立事务：业务回滚了审计还在，表里会出现"改了 3000"但实际仍是 1500 的假记录；</li>
 *   <li>同事务：业务回滚则审计一起没，但至少不会出现对不上的记录。</li>
 * </ul>
 * 审计的用途是"复盘真实发生过的变更"，假记录比没记录更糟，所以选同事务。
 * 同理，这里**不吞异常**：写不进去就整个操作失败。审计表写不进去通常意味着库出了问题，
 * 此时让业务成功才是真正危险的。
 *
 * <p>【字段长度必须截断】
 * old_value/new_value 只有 500 字符，而备注、退回原因这类文本很容易超。
 * 不截断就会在插入时报 "Data too long"，把一个本来成功的业务改成 500 错误。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private static final int VALUE_MAX = 500;
    /** 系统自动执行的变更（迁移、定时任务）没有登录人，统一记为 0 */
    private static final long SYSTEM_OPERATOR = 0L;

    private final AuditLogMapper auditLogMapper;

    /** 记录单个字段的变更 */
    public void record(String bizType, Long bizId, String field, Object oldValue, Object newValue) {
        save(bizType, bizId, field, str(oldValue), str(newValue));
    }

    /** 记录一条事件（没有具体新旧值，如"重置密码"、"退回稿件"） */
    public void event(String bizType, Long bizId, String what) {
        save(bizType, bizId, what, null, null);
    }

    /**
     * 记录整表策略调整：把 N 条变更写成一条摘要。
     *
     * <p>阶梯/底薪政策每次保存都是整表替换，逐行记会产生几十条噪声；
     * 记成 "0-20000=40% | 20000-25000=45% ..." 一条，人工看得懂也够用。
     */
    public void policy(String bizType, Long bizId, String before, String after) {
        save(bizType, bizId, "policy", before, after);
    }

    private void save(String bizType, Long bizId, String field, String oldValue, String newValue) {
        CurrentUser user = CurrentUserHolder.get();
        AuditLog row = new AuditLog();
        row.setBizType(cut(bizType, 30));
        row.setBizId(bizId == null ? 0L : bizId);
        row.setFieldName(cut(field, 50));
        row.setOldValue(cut(oldValue, VALUE_MAX));
        row.setNewValue(cut(newValue, VALUE_MAX));
        row.setOperatorId(user == null ? SYSTEM_OPERATOR : user.employeeId());
        row.setIp(clientIp());
        auditLogMapper.insert(row);
        log.debug("审计 {}#{} {} => {}", bizType, bizId, field, newValue);
    }

    /**
     * 取真实客户端 IP。
     *
     * <p>内部系统一般直连或走一层 Nginx，取 X-Forwarded-For 的第一段。
     * 这个值**只用于追溯，不能用于鉴权** —— 请求头是客户端可伪造的。
     */
    private String clientIp() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return null;
        }
        HttpServletRequest request = attrs.getRequest();
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return cut(forwarded.split(",")[0].trim(), 45);
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return cut(realIp.trim(), 45);
        }
        return cut(request.getRemoteAddr(), 45);
    }

    private static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String cut(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max - 3) + "...";
    }
}
