package com.petvacay.booking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * El escaneo de componentes implicito de {@code @SpringBootApplication} solo
 * cubre {@code com.petvacay.booking} y sus subpaquetes: no alcanza
 * {@code com.petvacay.shared}, que vive en el modulo {@code common} (otro jar,
 * paquete hermano, no descendiente). Hay que declararlo explicitamente para que
 * {@code GlobalExceptionHandler} se registre y RFC 7807 siga cubriendo tambien
 * los errores que no son de negocio.
 */
@SpringBootApplication
@ComponentScan(basePackages = {"com.petvacay.booking", "com.petvacay.shared"})
@EnableScheduling
public class BookingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(BookingServiceApplication.class, args);
    }
}
