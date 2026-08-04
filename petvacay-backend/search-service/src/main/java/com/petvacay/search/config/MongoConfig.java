package com.petvacay.search.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

/**
 * search-service posee un unico agregado (la vista de lectura del buscador) y su
 * unica razon de ser es servirla a baja latencia, asi que MongoDB es su unico
 * almacen — a diferencia de Fase 1-3, donde este mismo modelo de lectura convivia
 * como alternativa "sin infraestructura" a JPA/H2 dentro del monolito. Una vez
 * search-service es su propio despliegue, esa alternativa deja de aportar nada
 * (nadie mas comparte su JVM) y solo anadia una condicional y un adaptador
 * paralelo sin tests que lo ejercitaran. Se retira aqui: para desarrollar contra
 * este servicio basta `docker compose up -d mongodb`, igual que ya hace falta
 * `docker compose up -d kafka` para el transporte de eventos.
 */
@Configuration
@EnableMongoRepositories(basePackages = "com.petvacay.search.adapters.out.mongo")
public class MongoConfig {
}
