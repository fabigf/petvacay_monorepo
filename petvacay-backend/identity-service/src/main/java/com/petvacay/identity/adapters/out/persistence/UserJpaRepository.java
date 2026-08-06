package com.petvacay.identity.adapters.out.persistence;

import com.petvacay.identity.domain.model.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserJpaRepository extends JpaRepository<UserEntity, UUID> {

    Optional<UserEntity> findByEmail(String email);

    boolean existsByEmail(String email);

    /**
     * Busqueda del back-office: texto sobre nombre, apellidos y email, mas rol y
     * estado de la cuenta. Cada filtro se anula pasando {@code null}.
     *
     * <p>Compara en minusculas para que buscar "Ana" encuentre tambien "ana": un
     * buscador de personas que distingue mayusculas no sirve de nada.
     *
     * <p>El rol se comprueba con {@code member of} porque una cuenta tiene un
     * conjunto de roles, no uno solo — casi todos los cuidadores son tambien
     * clientes.
     */
    @Query("""
            select u from UserEntity u
            where (:text is null
                   or lower(u.firstName) like :text
                   or lower(u.lastName) like :text
                   or lower(u.email) like :text)
              and (:role is null or :role member of u.roles)
              and (:active is null or u.active = :active)
            order by u.createdAt desc
            """)
    Page<UserEntity> search(@Param("text") String text,
                            @Param("role") Role role,
                            @Param("active") Boolean active,
                            Pageable pageable);
}
