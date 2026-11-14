package com.farm.platform;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 启动类：扫包 + 开定时任务（9 个 @Scheduled）+ 开异步（日志落库）+ 扫 Mapper */
@SpringBootApplication   // 【启动类】
@EnableScheduling
@EnableAsync
@MapperScan("com.farm.platform.mapper")   // 扫描 Mapper 接口包
public class FarmPlatformApplication {

    /** 后端唯一入口 */
    public static void main(String[] args) {
        SpringApplication.run(FarmPlatformApplication.class, args);
    }
}
