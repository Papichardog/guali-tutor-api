package com.guali.tutor.api.exception;

import com.guali.tutor.api.dto.ApiError;
import com.guali.tutor.api.dto.ErrorCampo;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;
import java.util.Map;

/**
 * Traduce toda excepción a la estructura de error del contrato ({@link ApiError}), de modo que el
 * cliente reciba siempre el mismo formato: errores de negocio, JSON malformado, ids inválidos,
 * errores propios de Spring MVC (ruta inexistente, método o tipo de contenido no soportado) y
 * fallos inesperados (sin exponer detalles internos).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Errores de negocio y de validación lanzados por el parser y los servicios. */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> manejarApiException(ApiException ex, HttpServletRequest request) {
        return construir(ex.getStatus(), ex.getError(), ex.getCodigo(), ex.getMessage(),
                request, ex.getErrors(), HttpHeaders.EMPTY);
    }

    /** JSON malformado, cuerpo ausente, o un cuerpo que no es un objeto JSON. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> manejarCuerpoIlegible(HttpMessageNotReadableException ex,
                                                                      HttpServletRequest request) {
        return manejarApiException(ApiException.cuerpoInvalido(), request);
    }

    /** El {@code id} de la ruta no es un entero (p. ej. /api/configuracion/abc). */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> manejarParametroInvalido(MethodArgumentTypeMismatchException ex,
                                                                        HttpServletRequest request) {
        String mensaje = "id".equals(ex.getName())
                ? "El identificador '" + ex.getValue() + "' no es un entero válido."
                : "El parámetro '" + ex.getName() + "' tiene un valor inválido.";
        return manejarApiException(ApiException.parametroInvalido(mensaje), request);
    }

    /**
     * Todo lo demás. Las excepciones de Spring MVC que implementan {@link ErrorResponse} (ruta inexistente,
     * 405, 415...) conservan su código HTTP; cualquier otra es un fallo inesperado: 500 sin detalles
     * internos (se registran en el log del servidor).
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> manejarInesperado(Exception ex, HttpServletRequest request) {
        if (ex instanceof ErrorResponse errorMvc) {
            int status = errorMvc.getStatusCode().value();
            HttpStatus http = HttpStatus.resolve(status);
            String nombre = http != null ? http.name() : "ERROR";
            return switch (status) {
                case 404 -> construir(404, "NOT_FOUND", "RECURSO_NO_ENCONTRADO",
                        "No existe el recurso solicitado.", request, null, errorMvc.getHeaders());
                case 405 -> construir(405, nombre, "PETICION_NO_SOPORTADA",
                        "Método HTTP no permitido para este recurso.", request, null, errorMvc.getHeaders());
                case 415 -> construir(415, nombre, "PETICION_NO_SOPORTADA",
                        "Tipo de contenido no soportado. Envíe el cuerpo como application/json.",
                        request, null, errorMvc.getHeaders());
                default -> construir(status, nombre, "PETICION_NO_SOPORTADA",
                        "La solicitud no pudo procesarse.", request, null, errorMvc.getHeaders());
            };
        }
        log.error("Error inesperado en {} {}", request.getMethod(), request.getRequestURI(), ex);
        return construir(500, "INTERNAL_SERVER_ERROR", "ERROR_INTERNO",
                "Ocurrió un error interno. Intente de nuevo más tarde.", request, null, HttpHeaders.EMPTY);
    }

    private static ResponseEntity<Map<String, Object>> construir(int status, String error, String codigo, String mensaje,
                                                                 HttpServletRequest request, List<ErrorCampo> errores,
                                                                 HttpHeaders cabeceras) {
        ApiError cuerpo = ApiError.de(status, error, codigo, mensaje, request.getRequestURI(), errores);
        return ResponseEntity.status(status).headers(cabeceras).body(cuerpo.comoMapa());
    }
}
