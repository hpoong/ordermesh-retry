package com.hopoong.account;

import com.hopoong.core.CoreConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Import(CoreConfig.class)
@SpringBootApplication
@EnableJpaRepositories(basePackages = "com.hopoong.account.repository")
@EntityScan(basePackages = "com.hopoong.account.entity")
public class AccountServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AccountServiceApplication.class, args);
    }
}
