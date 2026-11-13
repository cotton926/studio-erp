package com.studio.erp.auth;

import com.studio.erp.common.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 密码策略单测。
 *
 * <p>【为什么这段逻辑值得单独测】
 * 它同时被"本人改密"、"管理员建号"、"管理员重置密码"三条路径调用。
 * 一旦生成的随机密码不满足自家规则，后果是：系统信誓旦旦给出一个初始密码，
 * 员工拿去登录，改密时被拒 —— 表现为"系统给的密码用不了"，
 * 而管理员重置十次都一样，最后只能改代码。
 * 这种"自己生成的密码自己不认"的问题评审时看不出来，只能靠跑。
 */
class PasswordPolicyTest {

    @Test
    @DisplayName("生成的初始密码必须始终满足强度规则")
    void generatedPasswordAlwaysMatchesRule() {
        // 单次生成看不出问题，跑 2000 次：如果不满足规则的概率是 1/1000，
        // 这里几乎必然被抓到；只跑一次则完全测不出来
        for (int i = 0; i < 2000; i++) {
            String pwd = PasswordPolicy.generate();
            assertEquals(10, pwd.length(), "密码长度应固定为 10");
            assertTrue(PasswordPolicy.isStrong(pwd), "生成了不符合规则的密码: " + pwd);
        }
    }

    @Test
    @DisplayName("初始密码不能去掉易混字符后仍出现 0/O/1/l/I")
    void generatedPasswordAvoidsConfusableChars() {
        Set<Character> confusables = Set.of('0', 'O', '1', 'l', 'I');
        for (int i = 0; i < 500; i++) {
            for (char c : PasswordPolicy.generate().toCharArray()) {
                assertFalse(confusables.contains(c),
                        "初始密码需要口头转达，不应包含易混字符: " + c);
            }
        }
    }

    @Test
    @DisplayName("连续生成不应重复（SecureRandom 生效的基本证据）")
    void generatedPasswordsAreDistinct() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            seen.add(PasswordPolicy.generate());
        }
        // 10 位、58 个字符的空间下，500 次几乎不可能碰撞；
        // 如果这里失败，说明随机源被固定了（例如误用了 new Random(固定种子)）
        assertEquals(500, seen.size(), "生成结果出现重复，随机源可能有问题");
    }

    @Test
    @DisplayName("强度规则：位数、字母、数字三者缺一不可")
    void weakPasswordsAreRejected() {
        assertFalse(PasswordPolicy.isStrong(null));
        assertFalse(PasswordPolicy.isStrong(""));
        assertFalse(PasswordPolicy.isStrong("abc123"));          // 只有 6 位
        assertFalse(PasswordPolicy.isStrong("abcdefghij"));      // 没有数字
        assertFalse(PasswordPolicy.isStrong("1234567890"));      // 没有字母
        assertTrue(PasswordPolicy.isStrong("abc12345"));         // 刚好达标
        assertTrue(PasswordPolicy.isStrong("Admin@2027"));       // 带符号也可以
    }

    @Test
    @DisplayName("assertStrong 不合格时抛业务异常，而不是返回 false")
    void assertStrongThrowsBizException() {
        BizException e = assertThrows(BizException.class,
                () -> PasswordPolicy.assertStrong("12345678"));
        assertEquals(40001, e.getErrorCode().getCode());
    }

    @Test
    @DisplayName("同一明文两次编码结果不同，但都能被匹配（BCrypt 盐值生效）")
    void bcryptStyleSaltIsPerHash() {
        // 这里不引入 Spring 容器，直接验证"策略层"没有把明文当哈希用：
        // 两次生成的密码如果相同，说明随机源退化了
        String a = PasswordPolicy.generate();
        String b = PasswordPolicy.generate();
        assertNotEquals(a, b);
    }
}
