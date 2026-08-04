package com.petvacay.identity.domain.model;

import com.petvacay.shared.domain.BusinessRuleViolationException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** Cuenta de usuario: cliente, cuidador o administrador. */
public class UserAccount {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final int MINIMUM_AGE = 18;

    private final UUID id;
    private final String email;
    private final String passwordHash;
    private final String firstName;
    private final String lastName;
    private final LocalDate birthDate;
    private final Set<Role> roles;
    private final Instant createdAt;
    private boolean active;

    private UserAccount(UUID id, String email, String passwordHash, String firstName, String lastName,
                        LocalDate birthDate, Set<Role> roles, Instant createdAt, boolean active) {
        this.id = Objects.requireNonNull(id, "id");
        this.email = Objects.requireNonNull(email, "email").toLowerCase();
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
        this.firstName = Objects.requireNonNull(firstName, "firstName");
        this.lastName = Objects.requireNonNull(lastName, "lastName");
        this.birthDate = Objects.requireNonNull(birthDate, "birthDate");
        this.roles = Set.copyOf(Objects.requireNonNull(roles, "roles"));
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.active = active;
    }

    public static UserAccount register(UUID id, String email, String passwordHash, String firstName,
                                       String lastName, LocalDate birthDate, Set<Role> roles, Instant now) {
        if (email == null || !EMAIL.matcher(email).matches()) {
            throw BusinessRuleViolationException.unprocessable(
                    "ERR_INVALID_EMAIL", "Email inválido", "El correo electrónico no tiene un formato válido.");
        }
        if (roles.contains(Role.ROLE_SITTER) && !isAdult(birthDate, now)) {
            throw BusinessRuleViolationException.unprocessable(
                    "ERR_SITTER_UNDERAGE", "Cuidador menor de edad",
                    "Para ofrecer servicios de cuidado es obligatorio ser mayor de 18 años.");
        }
        return new UserAccount(id, email, passwordHash, firstName, lastName, birthDate, roles, now, true);
    }

    public static UserAccount rehydrate(UUID id, String email, String passwordHash, String firstName,
                                        String lastName, LocalDate birthDate, Set<Role> roles,
                                        Instant createdAt, boolean active) {
        return new UserAccount(id, email, passwordHash, firstName, lastName, birthDate, roles, createdAt, active);
    }

    private static boolean isAdult(LocalDate birthDate, Instant now) {
        LocalDate today = now.atZone(java.time.ZoneOffset.UTC).toLocalDate();
        return Period.between(birthDate, today).getYears() >= MINIMUM_AGE;
    }

    /** Expulsion fulminante ejecutada por un administrador. */
    public void deactivate() {
        this.active = false;
    }

    public boolean hasRole(Role role) {
        return roles.contains(role);
    }

    public UUID id() {
        return id;
    }

    public String email() {
        return email;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public String firstName() {
        return firstName;
    }

    public String lastName() {
        return lastName;
    }

    public LocalDate birthDate() {
        return birthDate;
    }

    public Set<Role> roles() {
        return roles;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public boolean isActive() {
        return active;
    }
}
