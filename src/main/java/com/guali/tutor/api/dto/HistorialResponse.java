package com.guali.tutor.api.dto;

/**
 * Un intento en las respuestas (schema {@code Historial}). {@code leccionId} es {@code null} para lecciones locales o
 * si la lección se eliminó; {@code horaApertura} va como texto ISO local sin zona horaria; {@code estado}, como
 * "Pendiente" o "Completada".
 */
public record HistorialResponse(Integer id, Integer leccionId, String nombreLeccion, String horaApertura,
                                String estado) {
}
