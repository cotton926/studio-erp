package com.studio.erp.common.config;

import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 时间字段的 JSON 格式统一。
 *
 * <p>【为什么必须专门配】
 * application.yml 里的 {@code spring.jackson.date-format} 只对 java.util.Date 生效，
 * 对 LocalDateTime 完全无效 —— 它由 JavaTimeModule 用 ISO-8601 序列化，
 * 输出成 {@code 2026-11-13T00:00:17}。前端拿到带 T 的字符串，
 * 要么 Date 解析成 NaN，要么显示成"2026-11-13T00:00:17"这种原始串。
 *
 * <p>【为什么不用 yml 里的 date-format 兜底】
 * 两套格式并存时，哪个字段用哪个取决于类型，排查起来极其费时。
 * 统一在这里定义一遍，全项目只有一处真相：日期时间 yyyy-MM-dd HH:mm:ss，日期 yyyy-MM-dd。
 *
 * <p>【为什么反序列化也要配】
 * 只配序列化的话，前端回传"2026-11-13 10:00:00"会解析失败，
 * 而这种错误通常只在保存某个带时间的表单时才暴露，测试很容易漏掉。
 */
@Configuration
public class JacksonConfig {

    private static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";
    private static final String DATE_PATTERN = "yyyy-MM-dd";

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer localDateTimeCustomizer() {
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(DATE_TIME_PATTERN);
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern(DATE_PATTERN);

        return builder -> {
            SimpleModule module = new SimpleModule();
            module.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(dateTimeFormatter));
            module.addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer(dateTimeFormatter));
            module.addSerializer(LocalDate.class, new LocalDateSerializer(dateFormatter));
            module.addDeserializer(LocalDate.class, new LocalDateDeserializer(dateFormatter));
            builder.modulesToInstall(module);
        };
    }
}
