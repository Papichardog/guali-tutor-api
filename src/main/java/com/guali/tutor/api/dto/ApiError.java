package com.guali.tutor.api.dto;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Estructura uniforme de error (schema {@code Error} del contrato). Todas las respuestas de
 * error de la API tienen esta forma; el cliente decide con {@code codigo}, no con {@code message}.
 */
public record ApiError(int status, String error, String codigo, String message, String path,
                       String timestamp, List<ErrorCampo> errors) {

    /** Fecha-hora local sin zona horaria y siempre con segundos, como exige el contrato. */
    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    public static ApiError de(int status, String error, String codigo, String message, String path,
                              List<ErrorCampo> errors) {
        return new ApiError(status, error, codigo, message, path, LocalDateTime.now().format(FORMATO), errors);
    }

    /**
     * Cuerpo JSON de la respuesta. Se arma como mapa (y no se serializa el record directamente) para que
     * {@code errors} solo aparezca cuando hay errores de campo, tal como lo define el contrato.
     */
    public Map<String, Object> comoMapa() {
        Map<String, Object> mapa = new LinkedHashMap<>();
        mapa.put("status", status);
        mapa.put("error", error);
        mapa.put("codigo", codigo);
        mapa.put("message", message);
        mapa.put("path", path);
        mapa.put("timestamp", timestamp);
        if (errors != null && !errors.isEmpty()) {
            mapa.put("errors", errors);
        }
        return mapa;
    }
}
