package com.petvacay.shared.domain;

import java.util.List;

/**
 * Tramo de una lista larga, con lo necesario para pintar un paginador.
 *
 * <p>Es un tipo propio y no el {@code Page} de Spring Data a proposito: los
 * puertos de entrada son la frontera de la aplicacion y no deben obligar a quien
 * los use — ni a quien los implemente — a depender de la libreria de
 * persistencia. La traduccion ocurre una sola vez, en el adaptador.
 *
 * @param totalElements total sin paginar; es lo que permite al cliente saber
 *                      cuantas paginas hay sin recorrerlas todas.
 */
public record Page<T>(List<T> content, int page, int size, long totalElements) {

    public Page {
        content = List.copyOf(content);
    }

    public static <T> Page<T> of(List<T> content, int page, int size, long totalElements) {
        return new Page<>(content, page, size, totalElements);
    }

    /** Numero total de paginas; util para no tener que calcularlo en cada vista. */
    public int totalPages() {
        return size <= 0 ? 0 : (int) Math.ceil((double) totalElements / size);
    }
}
