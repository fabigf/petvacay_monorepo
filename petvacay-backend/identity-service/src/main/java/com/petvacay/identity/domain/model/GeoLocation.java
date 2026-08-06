package com.petvacay.identity.domain.model;

/**
 * Domicilio del cuidador: las coordenadas que usa el buscador geografico y la
 * direccion postal completa de la que salieron.
 *
 * <p>Van juntas y no en campos sueltos del perfil porque son la misma cosa vista
 * de dos maneras: el texto es para las personas — el cuidador la escribe, el
 * administrador la verifica, el cliente la recibe al confirmarse la reserva — y
 * las coordenadas son para calcular distancias. Separarlas invita a actualizar
 * una y olvidar la otra, y entonces el perfil diria vivir en un sitio y aparecer
 * en el mapa en otro.
 *
 * <p>{@code streetNumber} y {@code floorDoor} se guardan aparte de
 * {@code address} porque el geocodificador no los conoce: OpenStreetMap sabe
 * donde esta una calle, pero el piso y la puerta no estan mapeados en ningun
 * sitio — solo los sabe quien vive alli. Sin ellos, un repartidor de mascotas
 * llegaria al portal y no sabria a que timbre llamar.
 *
 * @param address     direccion legible devuelta por el geocodificador; puede ser
 *                    {@code null} en los perfiles creados antes de que se
 *                    pidiera, porque no se deduce de unas coordenadas.
 * @param streetNumber numero de la via.
 * @param floorDoor   planta y puerta; vacio en viviendas unifamiliares, donde no
 *                    existe tal cosa.
 */
public record GeoLocation(double latitude, double longitude, String address,
                          String streetNumber, String floorDoor) {

    public GeoLocation {
        if (latitude < -90 || latitude > 90) {
            throw new IllegalArgumentException("Latitud fuera de rango: " + latitude);
        }
        if (longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("Longitud fuera de rango: " + longitude);
        }
        address = blankToNull(address);
        streetNumber = blankToNull(streetNumber);
        floorDoor = blankToNull(floorDoor);
    }

    /** Ubicacion sin direccion postal conocida: solo el punto en el mapa. */
    public GeoLocation(double latitude, double longitude) {
        this(latitude, longitude, null, null, null);
    }

    /**
     * Direccion de una linea, tal y como se le enseñaria a alguien que tiene que
     * llegar hasta la puerta.
     */
    public String fullAddress() {
        if (address == null) {
            return null;
        }

        StringBuilder full = new StringBuilder(address);
        if (streetNumber != null) {
            full.append(", nº ").append(streetNumber);
        }
        if (floorDoor != null) {
            full.append(", ").append(floorDoor);
        }
        return full.toString();
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
