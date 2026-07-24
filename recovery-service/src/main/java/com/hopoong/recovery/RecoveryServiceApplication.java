package com.hopoong.recovery;

import com.hopoong.core.CoreConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@Import(CoreConfig.class)
@SpringBootApplication
public class RecoveryServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(RecoveryServiceApplication.class, args);
    }

}
