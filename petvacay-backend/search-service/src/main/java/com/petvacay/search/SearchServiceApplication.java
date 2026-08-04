package com.petvacay.search;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Ver el javadoc de {@code BookingServiceApplication} sobre por que hace falta
 * declarar explicitamente {@code com.petvacay.shared} en el escaneo.
 */
@SpringBootApplication
@ComponentScan(basePackages = {"com.petvacay.search", "com.petvacay.shared"})
public class SearchServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SearchServiceApplication.class, args);
    }
}
