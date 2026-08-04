package com.petvacay.search.config;

import com.petvacay.search.ports.out.SitterReadModelPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Vacia el indice de busqueda al arrancar en desarrollo.
 *
 * <p>Antes de la Fase 4, un unico {@code DevDataSeeder} en el monolito llamaba a
 * este mismo {@code removeAll()} justo antes de re-sembrar todos los datos, para
 * que el buscador no arrastrase cuidadores de arranques anteriores (identity
 * usa H2 en memoria, asi que cada reinicio genera UUIDs nuevos). Con
 * identity-service como proceso aparte, ya no puede alcanzar el almacen de
 * search-service para hacer esa limpieza: cada servicio pasa a responsabilizarse
 * de su propio ciclo de vida de datos de desarrollo.
 *
 * <p>Solo actua si {@code petvacay.seed.enabled=true} (perfil {@code dev});
 * en el perfil {@code postgres}/docker esta desactivado a proposito para no
 * arriesgarse a vaciar datos reales con un flag mal puesto.
 */
@Configuration
@ConditionalOnProperty(name = "petvacay.seed.enabled", havingValue = "true")
public class SearchDevDataCleaner {

    private static final Logger log = LoggerFactory.getLogger(SearchDevDataCleaner.class);

    @Bean
    ApplicationRunner cleanSearchIndexOnStartup(SitterReadModelPort readModel) {
        return args -> {
            readModel.removeAll();
            log.info("Indice de busqueda vaciado al arrancar (perfil dev)");
        };
    }
}
