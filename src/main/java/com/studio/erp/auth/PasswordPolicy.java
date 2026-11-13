package com.studio.erp.auth;

import com.studio.erp.common.exception.BizException;
import com.studio.erp.common.exception.ErrorCode;

import java.security.SecureRandom;
import java.util.regex.Pattern;

/**
 * 密码策略：强度校验 + 初始密码生成。
 *
 * <p>【为什么从各个 Service 里抽出来】
 * 原来改密用一套正则、管理员初始化用另一套（内容一样但复制了两份）。
 * 这类"看起来一样"的重复最危险：哪天策略要加一条"不能包含用户名"，
 * 改了一处漏了另一处，就等于留着一条弱口令通道。
 *
 * <p>【规则为什么是"至少 8 位 + 字母 + 数字"，不强制特殊符号】
 * 设计公司不是银行，强制符号的后果是大家把密码写在便签贴显示器上 ——
 * 安全性反而更低。8 位含字母数字、配合登录失败锁定，已经足够挡住
 * 内网里的暴力尝试；真正要防的是"共用账号"和"密码外传"，靠制度而不是复杂度。
 */
public final class PasswordPolicy {

    /** 8~64 位，必须同时包含字母和数字 */
    private static final Pattern RULE = Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d).{8,64}$");

    /**
     * 初始密码字符集：去掉了 0/O、1/l/I 这几组。
     * 原因很实际 —— 初始密码要由主管口头或微信转达给员工，
     * "是数字零还是字母欧"会导致员工连续登录失败，然后来找管理员重置。
     */
    private static final char[] ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789".toCharArray();

    private static final int RANDOM_LENGTH = 10;

    private PasswordPolicy() {
    }

    /**
     * 校验强度，不合格直接抛业务异常。
     *
     * @param rawPassword 明文密码，调用方保证非 null
     */
    public static void assertStrong(String rawPassword) {
        if (rawPassword == null || !RULE.matcher(rawPassword).matches()) {
            throw new BizException(ErrorCode.PASSWORD_TOO_WEAK);
        }
    }

    public static boolean isStrong(String rawPassword) {
        return rawPassword != null && RULE.matcher(rawPassword).matches();
    }

    /**
     * 生成初始密码。
     *
     * <p>【为什么用 SecureRandom 而不是 Random】
     * {@code new Random()} 用系统时间做种子，同一秒内启动的进程会拿到
     * 完全相同的序列；知道创建时间就能推算出密码。初始密码是账号唯一凭证，
     * 用可预测的随机源等于没有随机。
     *
     * <p>【为什么先生成再打乱，而不是纯随机拼 10 位】
     * 纯随机有极小概率一个数字都没抽到，生成的密码又恰好不满足自己的强度规则，
     * 员工首次登录时改密会被拒，表现为"系统给了个错密码"。
     * 先固定放 1 个字母 + 1 个数字，再补足位数并打乱，规则必然满足。
     */
    public static String generate() {
        SecureRandom random = new SecureRandom();
        char[] chars = new char[RANDOM_LENGTH];

        // 前两位先保证满足"含字母 + 含数字"这条硬规则
        chars[0] = randomLetter(random);
        chars[1] = randomDigit(random);
        for (int i = 2; i < RANDOM_LENGTH; i++) {
            chars[i] = ALPHABET[random.nextInt(ALPHABET.length)];
        }

        // Fisher-Yates 洗牌：不洗的话密码永远是"字母+数字+随机"的固定形状，
        // 目测就能看出规律，等于把搜索空间缩小了一个量级
        for (int i = chars.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            char tmp = chars[i];
            chars[i] = chars[j];
            chars[j] = tmp;
        }
        return new String(chars);
    }

    private static char randomLetter(SecureRandom random) {
        // 只在小写/大写字母段里取，避开刚才的字符集里被剔除的字母
        char c;
        do {
            c = ALPHABET[random.nextInt(ALPHABET.length)];
        } while (!Character.isLetter(c));
        return c;
    }

    private static char randomDigit(SecureRandom random) {
        char c;
        do {
            c = ALPHABET[random.nextInt(ALPHABET.length)];
        } while (!Character.isDigit(c));
        return c;
    }
}
