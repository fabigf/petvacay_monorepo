package com.petvacay.identity;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Ver el javadoc de {@code BookingServiceApplication} sobre por que hace falta
 * declarar explicitamente {@code com.petvacay.shared} en el escaneo.
 */
@SpringBootApplication
@ComponentScan(basePackages = {"com.petvacay.identity", "com.petvacay.shared"})
public class IdentityServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(IdentityServiceApplication.class, args);
    }
}
