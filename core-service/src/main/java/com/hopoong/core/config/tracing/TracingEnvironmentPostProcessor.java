package com.hopoong.core.config.tracing;

import java.io.IOException;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

public class TracingEnvironmentPostProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        ClassPathResource resource = new ClassPathResource("tracing-defaults.yml");
        if (!resource.exists()) {
            return;
        }
        try {
            YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
            for (PropertySource<?> propertySource : loader.load("tracing-defaults", resource)) {
                environment.getPropertySources().addLast(propertySource);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load tracing-defaults.yml", exception);
        }
    }
}
