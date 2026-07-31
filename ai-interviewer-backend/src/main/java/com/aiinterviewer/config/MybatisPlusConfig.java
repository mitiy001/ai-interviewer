package com.aiinterviewer.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.aiinterviewer.mapper")
public class MybatisPlusConfig {
}
