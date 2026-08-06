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
    private final String passwordHash;
    private final LocalDate birthDate;
    private final Set<Role> roles;
    private final Instant createdAt;

    // Mutables desde que el back-office puede corregir los datos de una cuenta
    // (negocio.md 2.3). El resto sigue siendo inmutable a proposito: cambiar la
    // fecha de nacimiento o los roles no es "corregir una errata", es alterar
    // quien es alguien y que puede hacer.
    private String email;
    private String firstName;
    private String lastName;
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

    /**
     * Correccion de los datos de contacto por un administrador.
     *
     * <p>El email se valida con la misma regla que en el alta: es la credencial
     * con la que se inicia sesion, asi que dejarlo con un formato invalido
     * dejaria la cuenta inaccesible. La unicidad no puede comprobarse aqui — el
     * agregado no ve a los demas usuarios — y la garantiza el servicio de
     * aplicacion antes de llamar.
     */
    public void updateContactDetails(String email, String firstName, String lastName) {
        if (email == null || !EMAIL.matcher(email).matches()) {
            throw BusinessRuleViolationException.unprocessable(
                    "ERR_INVALID_EMAIL", "Email inválido", "El correo electrónico no tiene un formato válido.");
        }
        this.email = email.toLowerCase();
        this.firstName = requireText(firstName, "ERR_FIRST_NAME_REQUIRED", "El nombre es obligatorio.");
        this.lastName = requireText(lastName, "ERR_LAST_NAME_REQUIRED", "Los apellidos son obligatorios.");
    }

    private static String requireText(String value, String code, String detail) {
        if (value == null || value.isBlank()) {
            throw BusinessRuleViolationException.unprocessable(code, "Dato obligatorio", detail);
        }
        return value.trim();
    }

    /**
     * Expulsion ejecutada por un administrador. Es un borrado logico: la cuenta
     * y su historial siguen existiendo — las reservas pasadas y su dinero no
     * pueden evaporarse porque se expulse a alguien — pero deja de poder entrar.
     */
    public void deactivate() {
        this.active = false;
    }

    /** Revierte la expulsion; el borrado logico existe justo para poder deshacerse. */
    public void reactivate() {
        this.active = true;
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
