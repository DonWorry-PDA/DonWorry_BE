package com.sol.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
@ComponentScan(basePackages = {"com.sol"})
public class UserModuleApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserModuleApplication.class, args);
    }
}
