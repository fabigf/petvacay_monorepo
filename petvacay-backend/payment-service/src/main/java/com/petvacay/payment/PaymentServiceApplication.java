package com.petvacay.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Ver el javadoc de {@code BookingServiceApplication} sobre por que hace falta
 * declarar explicitamente {@code com.petvacay.shared} en el escaneo.
 */
@SpringBootApplication
@ComponentScan(basePackages = {"com.petvacay.payment", "com.petvacay.shared"})
public class PaymentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
    }
}
