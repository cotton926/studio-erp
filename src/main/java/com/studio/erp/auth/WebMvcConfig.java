package com.studio.erp.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 层装配：登录拦截器 + 跨域。
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    /**
     * 开发期前端地址。上线时前后端同域部署，这里应当留空。
     * 用配置项而不是硬编码，是因为换端口（Vite 默认 5173，有时被占用会变 5174）
     * 就得改代码重新编译，非常烦人。
     */
    @Value("${app.cors.allowed-origin:http://localhost:5173}")
    private String allowedOrigin;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                // 登录接口本身当然不能要求已登录，否则永远登不上
                .excludePathPatterns("/api/auth/login");
    }

    /**
     * 跨域配置。
     *
     * <p>【为什么 allowCredentials(true) 时不能写 allowOrigins("*")】
     * 浏览器规范禁止 `*` 与凭证同时使用，Spring 会直接抛
     * "When allowCredentials is true, allowedOrigins cannot contain '*'"，启动即失败。
     * 这里指定具体来源，同时因为前端要把令牌放请求头，必须显式允许 studio-token。
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigin)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("studio-token")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
