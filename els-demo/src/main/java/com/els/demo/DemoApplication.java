package com.els.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@ComponentScan(basePackages = { "com.els.demo", "com.els" }) // Scan both demo and library
@EntityScan(basePackages = { "com.els.demo.domain", "com.els.domain" }) // Scan entities
@EnableJpaRepositories(basePackages = { "com.els.demo.repository", "com.els.repository" })
public class DemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
