package com.petvacay.identity.adapters.out.persistence;

import com.petvacay.identity.domain.model.VerificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface SitterProfileJpaRepository extends JpaRepository<SitterProfileEntity, UUID> {

    List<SitterProfileEntity> findByVerificationStatus(VerificationStatus status);

    /**
     * Busqueda del back-office: estado de verificacion sobre el perfil y texto
     * libre sobre los datos de la cuenta.
     *
     * <p>El {@code join} con {@code UserEntity} no sigue una asociacion mapeada
     * — un perfil y su cuenta comparten identificador pero son agregados
     * distintos y no se referencian entre si — sino que se hace por el propio
     * identificador. Cruzarlos aqui es lo que permite filtrar y paginar de una
     * vez: hacerlo en memoria despues devolveria paginas incompletas y un total
     * que no cuadra con lo que se ve.
     *
     * <p>Cada filtro se anula con {@code null}, para no multiplicar consultas
     * segun que combinacion venga marcada en la pantalla.
     */
    @Query("""
            select p from SitterProfileEntity p
            join UserEntity u on u.id = p.sitterId
            where (:status is null or p.verificationStatus = :status)
              and (:text is null
                   or lower(u.firstName) like :text
                   or lower(u.lastName) like :text
                   or lower(u.email) like :text)
            order by u.createdAt desc
            """)
    Page<SitterProfileEntity> search(@Param("status") VerificationStatus status,
                                     @Param("text") String text,
                                     Pageable pageable);
}
