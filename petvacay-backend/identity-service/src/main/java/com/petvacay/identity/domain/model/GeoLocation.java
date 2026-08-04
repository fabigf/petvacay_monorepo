package com.petvacay.identity.domain.model;

/** Coordenadas del domicilio del cuidador, usadas por el buscador geografico. */
public record GeoLocation(double latitude, double longitude) {

    public GeoLocation {
        if (latitude < -90 || latitude > 90) {
            throw new IllegalArgumentException("Latitud fuera de rango: " + latitude);
        }
        if (longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("Longitud fuera de rango: " + longitude);
        }
    }
}
