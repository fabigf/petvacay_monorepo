package com.petvacay.shared.domain;

/**
 * Excepcion de negocio traducida a una respuesta RFC 7807 por el adaptador web.
 * El {@code code} es el contrato estable que consume el frontend React
 * (ver especificaciones.md seccion 4).
 */
public class BusinessRuleViolationException extends RuntimeException {

    private final String code;
    private final String title;
    private final int status;

    public BusinessRuleViolationException(String code, String title, int status, String detail) {
        super(detail);
        this.code = code;
        this.title = title;
        this.status = status;
    }

    public static BusinessRuleViolationException conflict(String code, String title, String detail) {
        return new BusinessRuleViolationException(code, title, 409, detail);
    }

    public static BusinessRuleViolationException unprocessable(String code, String title, String detail) {
        return new BusinessRuleViolationException(code, title, 422, detail);
    }

    public String getCode() {
        return code;
    }

    public String getTitle() {
        return title;
    }

    public int getStatus() {
        return status;
    }
}
