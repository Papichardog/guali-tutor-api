package com.guali.tutor.api.exception;

import com.guali.tutor.api.dto.ErrorCampo;

import java.util.List;

/**
 * Error de negocio o de validación que la API traduce a la estructura de error del contrato
 * (ver {@link GlobalExceptionHandler}). No depende de Spring.
 */
@SuppressWarnings("serial")
public class ApiException extends RuntimeException {

    private final int status;
    private final String error;
    private final String codigo;
    private final List<ErrorCampo> errors;

    private ApiException(int status, String error, String codigo, String message, List<ErrorCampo> errors) {
        super(message);
        this.status = status;
        this.error = error;
        this.codigo = codigo;
        this.errors = errors;
    }

    /** 400 VALIDACION_FALLIDA: uno o más campos inválidos. */
    public static ApiException validacion(List<ErrorCampo> errores) {
        return new ApiException(400, "BAD_REQUEST", "VALIDACION_FALLIDA",
                "Hay campos inválidos en la solicitud.", errores);
    }

    /** 400 CUERPO_INVALIDO: JSON malformado o con propiedades desconocidas. */
    public static ApiException cuerpoInvalido() {
        return new ApiException(400, "BAD_REQUEST", "CUERPO_INVALIDO",
                "El cuerpo no es un JSON válido o contiene propiedades desconocidas.", null);
    }

    /** 400 PARAMETRO_INVALIDO: el id de la ruta no es un entero válido. */
    public static ApiException parametroInvalido(String mensaje) {
        return new ApiException(400, "BAD_REQUEST", "PARAMETRO_INVALIDO", mensaje, null);
    }

    /** 400 REFERENCIA_INVALIDA: un campo apunta a un recurso que no existe (p. ej. un leccionId inexistente). */
    public static ApiException referenciaInvalida(String mensaje, List<ErrorCampo> errores) {
        return new ApiException(400, "BAD_REQUEST", "REFERENCIA_INVALIDA", mensaje, errores);
    }

    /** 404 RECURSO_NO_ENCONTRADO. */
    public static ApiException noEncontrado(String mensaje) {
        return new ApiException(404, "NOT_FOUND", "RECURSO_NO_ENCONTRADO", mensaje, null);
    }

    /** 409 CONFLICT con el código de negocio indicado (p. ej. CONFIGURACION_DUPLICADA). */
    public static ApiException conflicto(String codigo, String mensaje) {
        return new ApiException(409, "CONFLICT", codigo, mensaje, null);
    }

    public int getStatus() {
        return status;
    }

    public String getError() {
        return error;
    }

    public String getCodigo() {
        return codigo;
    }

    public List<ErrorCampo> getErrors() {
        return errors;
    }
}
