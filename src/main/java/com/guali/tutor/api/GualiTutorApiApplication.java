package com.guali.tutor.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punto de entrada de la API REST de Guali Tutor.
 *
 * <p>Arquitectura: Controller -> Service -> Repository -> JPA -> MariaDB.
 * Los paquetes controller, service, repository, dao (entidades JPA), dto y
 * exception cuelgan de este paquete y se escanean automáticamente.
 */
@SpringBootApplication
public class GualiTutorApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(GualiTutorApiApplication.class, args);
    }
}