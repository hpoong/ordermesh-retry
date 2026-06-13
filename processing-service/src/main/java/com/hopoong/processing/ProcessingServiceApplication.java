package com.hopoong.processing;

import com.hopoong.core.CoreConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Import(CoreConfig.class)
@SpringBootApplication
@EnableJpaRepositories(basePackages = "com.hopoong.processing.repository")
@EntityScan(basePackages = "com.hopoong.processing.entity")
public class ProcessingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProcessingServiceApplication.class, args);
    }
}
