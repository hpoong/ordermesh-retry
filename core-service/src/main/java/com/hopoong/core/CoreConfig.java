package com.hopoong.core;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@ComponentScan(basePackages = "com.hopoong.core")
@EnableJpaRepositories(basePackages = "com.hopoong.core.repository")
@EntityScan(basePackages = "com.hopoong.core.entity")
public class CoreConfig { }
