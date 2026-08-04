package com.petvacay.identity.adapters.out.persistence;

import com.petvacay.identity.domain.model.Role;
import com.petvacay.identity.domain.model.UserAccount;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "users")
public class UserEntity {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role", nullable = false)
    @Enumerated(EnumType.STRING)
    private Set<Role> roles = new HashSet<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private boolean active;

    protected UserEntity() {
    }

    public static UserEntity fromDomain(UserAccount user) {
        UserEntity entity = new UserEntity();
        entity.id = user.id();
        entity.email = user.email();
        entity.passwordHash = user.passwordHash();
        entity.firstName = user.firstName();
        entity.lastName = user.lastName();
        entity.birthDate = user.birthDate();
        entity.roles = new HashSet<>(user.roles());
        entity.createdAt = user.createdAt();
        entity.active = user.isActive();
        return entity;
    }

    public UserAccount toDomain() {
        return UserAccount.rehydrate(id, email, passwordHash, firstName, lastName, birthDate,
                roles.isEmpty() ? EnumSet.noneOf(Role.class) : EnumSet.copyOf(roles), createdAt, active);
    }
}
