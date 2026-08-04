package com.petvacay.identity.domain.model;

import com.petvacay.shared.domain.BusinessRuleViolationException;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Ficha tecnica obligatoria de una mascota (negocio.md seccion 2.1).
 * El cuidador toma decisiones a partir de estos datos, por eso se validan aqui.
 */
public class Pet {

    private final UUID id;
    private final UUID ownerId;
    private final String name;
    private final Species species;
    private final String breed;
    private final int ageYears;
    private final BigDecimal weightKg;
    private final String medicalNeeds;
    private final String dietaryRestrictions;
    private final Sociability sociability;

    public Pet(UUID id, UUID ownerId, String name, Species species, String breed, int ageYears,
               BigDecimal weightKg, String medicalNeeds, String dietaryRestrictions, Sociability sociability) {
        this.id = Objects.requireNonNull(id, "id");
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId");
        this.name = requireText(name, "ERR_PET_NAME_REQUIRED", "El nombre de la mascota es obligatorio.");
        this.species = Objects.requireNonNull(species, "species");
        this.breed = breed;
        this.ageYears = ageYears;
        this.weightKg = Objects.requireNonNull(weightKg, "weightKg");
        this.medicalNeeds = medicalNeeds;
        this.dietaryRestrictions = dietaryRestrictions;
        this.sociability = Objects.requireNonNull(sociability, "sociability");

        if (ageYears < 0 || ageYears > 40) {
            throw BusinessRuleViolationException.unprocessable(
                    "ERR_PET_INVALID_AGE", "Edad inválida", "La edad de la mascota no es plausible.");
        }
        if (weightKg.signum() <= 0) {
            throw BusinessRuleViolationException.unprocessable(
                    "ERR_PET_INVALID_WEIGHT", "Peso inválido", "El peso de la mascota debe ser mayor que cero.");
        }
    }

    private static String requireText(String value, String code, String detail) {
        if (value == null || value.isBlank()) {
            throw BusinessRuleViolationException.unprocessable(code, "Dato obligatorio", detail);
        }
        return value.trim();
    }

    /** Indica si la mascota requiere atencion medica durante la estancia. */
    public boolean hasSpecialMedicalNeeds() {
        return medicalNeeds != null && !medicalNeeds.isBlank();
    }

    public UUID id() {
        return id;
    }

    public UUID ownerId() {
        return ownerId;
    }

    public String name() {
        return name;
    }

    public Species species() {
        return species;
    }

    public String breed() {
        return breed;
    }

    public int ageYears() {
        return ageYears;
    }

    public BigDecimal weightKg() {
        return weightKg;
    }

    public String medicalNeeds() {
        return medicalNeeds;
    }

    public String dietaryRestrictions() {
        return dietaryRestrictions;
    }

    public Sociability sociability() {
        return sociability;
    }
}
