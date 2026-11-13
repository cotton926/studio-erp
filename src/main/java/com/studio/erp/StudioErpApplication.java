package com.studio.erp;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 启动类。
 *
 * <p>【为什么 @MapperScan 放在这里而不是每个 Mapper 上写 @Mapper】
 * 一处声明，新增 Mapper 不会因为忘记加注解而注入失败（这种错只在启动时才暴露，很浪费时间）。
 */
@SpringBootApplication
@MapperScan("com.studio.erp.**.mapper")
@EnableScheduling
public class StudioErpApplication {

    public static void main(String[] args) {
        SpringApplication.run(StudioErpApplication.class, args);
    }
}
