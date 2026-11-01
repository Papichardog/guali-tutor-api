package com.guali.tutor.api.dto;

import com.guali.tutor.api.dao.EstadoHistorial;
import com.guali.tutor.api.exception.ApiException;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Convierte y valida los cuerpos JSON de POST, PUT y PATCH de /api/historial según el contrato OpenAPI, con el
 * mismo enfoque que los otros parsers: los cuerpos llegan como {@code Map} para distinguir un campo ausente de uno
 * con null (PUT exige los cuatro campos presentes; PATCH, merge patch, deja intacto lo ausente y solo admite null en
 * {@code leccionId}) y para rechazar propiedades desconocidas. Se informan todos los errores a la vez. No depende de Spring.
 */
public final class HistorialRequestParser {

    public static final int MAX_NOMBRE = 255;

    private static final String LECCION_ID = "leccionId";
    private static final String NOMBRE_LECCION = "nombreLeccion";
    private static final String HORA_APERTURA = "horaApertura";
    private static final String ESTADO = "estado";

    private static final Set<String> CAMPOS = Set.of(LECCION_ID, NOMBRE_LECCION, HORA_APERTURA, ESTADO);
    private static final Pattern FECHA_HORA = Pattern.compile("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{1,9})?");

    private HistorialRequestParser() {
    }

    /** POST: solo {@code nombreLeccion} es obligatorio; sin los demás, el servidor usa lección local, hora actual y Pendiente. */
    public static HistorialCreate parseCreate(Map<String, Object> cuerpo) {
        preparar(cuerpo);
        List<ErrorCampo> errores = new ArrayList<>();
        Integer leccionId = cuerpo.containsKey(LECCION_ID) ? leerLeccionId(cuerpo.get(LECCION_ID), errores) : null;
        String nombre = leerNombre(cuerpo, true, errores);
        LocalDateTime hora = cuerpo.containsKey(HORA_APERTURA) ? leerHora(valorNoNulo(cuerpo, HORA_APERTURA, errores), errores) : null;
        EstadoHistorial estado = cuerpo.containsKey(ESTADO) ? leerEstado(valorNoNulo(cuerpo, ESTADO, errores), errores) : null;
        lanzarSiHayErrores(errores);
        return new HistorialCreate(leccionId, nombre, hora, estado);
    }

    /** PUT: reemplazo completo; los cuatro campos deben venir ({@code leccionId} puede ser null). */
    public static HistorialUpdate parseUpdate(Map<String, Object> cuerpo) {
        preparar(cuerpo);
        List<ErrorCampo> errores = new ArrayList<>();
        Integer leccionId = null;
        if (!cuerpo.containsKey(LECCION_ID)) {
            errores.add(new ErrorCampo(LECCION_ID, "es obligatorio (envíe null si no hay lección)"));
        } else {
            leccionId = leerLeccionId(cuerpo.get(LECCION_ID), errores);
        }
        String nombre = leerNombre(cuerpo, true, errores);
        LocalDateTime hora = leerHora(valorNoNulo(cuerpo, HORA_APERTURA, errores, true), errores);
        EstadoHistorial estado = leerEstado(valorNoNulo(cuerpo, ESTADO, errores, true), errores);
        lanzarSiHayErrores(errores);
        return new HistorialUpdate(leccionId, nombre, hora, estado);
    }

    /** PATCH (merge patch): al menos un campo; lo ausente no cambia; null solo es válido en {@code leccionId} (desvincula). */
    public static HistorialPatch parsePatch(Map<String, Object> cuerpo) {
        preparar(cuerpo);
        if (cuerpo.isEmpty()) {
            throw ApiException.validacion(List.of(new ErrorCampo("(cuerpo)", "debe incluir al menos un campo")));
        }
        List<ErrorCampo> errores = new ArrayList<>();
        ValorPatch<Integer> leccionId = cuerpo.containsKey(LECCION_ID)
                ? new ValorPatch<>(leerLeccionId(cuerpo.get(LECCION_ID), errores)) : null;
        ValorPatch<String> nombre = cuerpo.containsKey(NOMBRE_LECCION)
                ? new ValorPatch<>(leerNombre(cuerpo, true, errores)) : null;
        ValorPatch<LocalDateTime> hora = cuerpo.containsKey(HORA_APERTURA)
                ? new ValorPatch<>(leerHora(valorNoNulo(cuerpo, HORA_APERTURA, errores), errores)) : null;
        ValorPatch<EstadoHistorial> estado = cuerpo.containsKey(ESTADO)
                ? new ValorPatch<>(leerEstado(valorNoNulo(cuerpo, ESTADO, errores), errores)) : null;
        lanzarSiHayErrores(errores);
        return new HistorialPatch(leccionId, nombre, hora, estado);
    }

    // ------------------------------------------------------------------

    /** Cuerpo presente y sin propiedades desconocidas (el contrato declara additionalProperties:false). */
    private static void preparar(Map<String, Object> cuerpo) {
        if (cuerpo == null || !CAMPOS.containsAll(cuerpo.keySet())) {
            throw ApiException.cuerpoInvalido();
        }
    }

    private static Object valorNoNulo(Map<String, Object> cuerpo, String campo, List<ErrorCampo> errores) {
        return valorNoNulo(cuerpo, campo, errores, false);
    }

    /** El valor de un campo que no admite null; si falta (y es obligatorio) o es null, registra el error y devuelve null. */
    private static Object valorNoNulo(Map<String, Object> cuerpo, String campo, List<ErrorCampo> errores,
                                      boolean obligatorio) {
        if (!cuerpo.containsKey(campo)) {
            if (obligatorio) {
                errores.add(new ErrorCampo(campo, "es obligatorio"));
            }
            return null;
        }
        Object valor = cuerpo.get(campo);
        if (valor == null) {
            errores.add(new ErrorCampo(campo, "no puede ser null"));
        }
        return valor;
    }

    /** leccionId: entero ≥ 1 o null. */
    private static Integer leerLeccionId(Object valor, List<ErrorCampo> errores) {
        if (valor == null) {
            return null;
        }
        if (valor instanceof Integer entero) {
            if (entero < 1) {
                errores.add(new ErrorCampo(LECCION_ID, "debe ser mayor o igual a 1"));
                return null;
            }
            return entero;
        }
        if (valor instanceof Long largo) {
            errores.add(new ErrorCampo(LECCION_ID, largo < 1
                    ? "debe ser mayor o igual a 1" : "debe ser menor o igual a " + Integer.MAX_VALUE));
            return null;
        }
        if (valor instanceof BigInteger) {
            errores.add(new ErrorCampo(LECCION_ID, "debe ser menor o igual a " + Integer.MAX_VALUE));
            return null;
        }
        errores.add(new ErrorCampo(LECCION_ID, "debe ser un número entero o null"));
        return null;
    }

    /** nombreLeccion: texto de 1 a 255 caracteres, no en blanco. Ausente o null es un error (ya lo explica el mensaje). */
    private static String leerNombre(Map<String, Object> cuerpo, boolean obligatorio, List<ErrorCampo> errores) {
        Object valor = valorNoNulo(cuerpo, NOMBRE_LECCION, errores, obligatorio);
        if (valor == null) {
            return null;
        }
        if (!(valor instanceof String texto)) {
            errores.add(new ErrorCampo(NOMBRE_LECCION, "debe ser texto"));
            return null;
        }
        if (texto.isBlank()) {
            errores.add(new ErrorCampo(NOMBRE_LECCION, "no puede estar en blanco"));
            return null;
        }
        if (texto.codePointCount(0, texto.length()) > MAX_NOMBRE) {
            errores.add(new ErrorCampo(NOMBRE_LECCION, "debe tener como máximo " + MAX_NOMBRE + " caracteres"));
            return null;
        }
        return texto;
    }

    /**
     * horaApertura: texto yyyy-MM-ddTHH:mm:ss (con fracción opcional) sin zona horaria. Se trunca a segundos, que es lo
     * que guarda la columna DATETIME, para que lo que la API responde sea exactamente lo guardado.
     */
    private static LocalDateTime leerHora(Object valor, List<ErrorCampo> errores) {
        if (valor == null) {
            return null; // ya se registró el error (ausente u null)
        }
        if (!(valor instanceof String texto) || !FECHA_HORA.matcher(texto).matches()) {
            errores.add(new ErrorCampo(HORA_APERTURA, "debe tener el formato yyyy-MM-ddTHH:mm:ss (sin zona horaria)"));
            return null;
        }
        try {
            return LocalDateTime.parse(texto).truncatedTo(ChronoUnit.SECONDS);
        } catch (DateTimeParseException e) {
            errores.add(new ErrorCampo(HORA_APERTURA, "no es una fecha y hora válida"));
            return null;
        }
    }

    private static EstadoHistorial leerEstado(Object valor, List<ErrorCampo> errores) {
        if (valor == null) {
            return null; // ya se registró el error (ausente u null)
        }
        if (!(valor instanceof String texto) || !EstadoHistorial.textos().contains(texto)) {
            errores.add(new ErrorCampo(ESTADO, "debe ser uno de: " + String.join(", ", EstadoHistorial.textos())));
            return null;
        }
        return EstadoHistorial.desdeTexto(texto);
    }

    private static void lanzarSiHayErrores(List<ErrorCampo> errores) {
        if (!errores.isEmpty()) {
            throw ApiException.validacion(errores);
        }
    }
}
