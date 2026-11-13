package com.studio.erp.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 密码编码器。
 *
 * <p>【cost 为什么用默认 10 而不是更高】
 * 10 约等于每次校验 60~80ms。登录接口一天可能只有几十次调用，这个成本无所谓；
 * 但如果调到 14，单次就要 1 秒，一旦有人写个批量导入员工的脚本，
 * 会直接把接口拖到超时。10 是"爆破成本足够高、正常使用无感"的平衡点。
 *
 * <p>【为什么不做成静态工具类】
 * 做成 Bean 才能被替换（比如测试环境用 cost=4 的快速编码器，否则单测会慢十倍），
 * 静态调用则永远绑死。
 */
@Configuration
public class AuthConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
