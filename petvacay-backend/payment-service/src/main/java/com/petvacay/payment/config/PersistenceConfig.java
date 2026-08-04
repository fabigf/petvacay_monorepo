package com.petvacay.payment.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EnableJpaRepositories(basePackages = "com.petvacay.payment.adapters.out.persistence")
public class PersistenceConfig {
}
