package com.catalyst;

import com.catalyst.decision.DecisionProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@org.springframework.data.jpa.repository.config.EnableJpaRepositories(basePackages = "com.catalyst", considerNestedRepositories = true)
@EnableConfigurationProperties({DecisionProperties.class, com.catalyst.demo.CatalystProperties.class})
public class CatalystApplication {
    public static void main(String[] args) {
        SpringApplication.run(CatalystApplication.class, args);
    }
}
