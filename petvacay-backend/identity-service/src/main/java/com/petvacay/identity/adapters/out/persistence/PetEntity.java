package com.petvacay.identity.adapters.out.persistence;

import com.petvacay.identity.domain.model.Pet;
import com.petvacay.identity.domain.model.Sociability;
import com.petvacay.identity.domain.model.Species;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "pets")
public class PetEntity {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Species species;

    private String breed;

    @Column(name = "age_years", nullable = false)
    private int ageYears;

    @Column(name = "weight_kg", nullable = false, precision = 6, scale = 2)
    private BigDecimal weightKg;

    @Column(name = "medical_needs", length = 2000)
    private String medicalNeeds;

    @Column(name = "dietary_restrictions", length = 2000)
    private String dietaryRestrictions;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Sociability sociability;

    protected PetEntity() {
    }

    public static PetEntity fromDomain(Pet pet) {
        PetEntity entity = new PetEntity();
        entity.id = pet.id();
        entity.ownerId = pet.ownerId();
        entity.name = pet.name();
        entity.species = pet.species();
        entity.breed = pet.breed();
        entity.ageYears = pet.ageYears();
        entity.weightKg = pet.weightKg();
        entity.medicalNeeds = pet.medicalNeeds();
        entity.dietaryRestrictions = pet.dietaryRestrictions();
        entity.sociability = pet.sociability();
        return entity;
    }

    public Pet toDomain() {
        return new Pet(id, ownerId, name, species, breed, ageYears, weightKg, medicalNeeds,
                dietaryRestrictions, sociability);
    }
}
