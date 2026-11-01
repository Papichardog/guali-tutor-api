package com.guali.tutor.api.dto;

import com.guali.tutor.api.dao.EstadoHistorial;

import java.time.LocalDateTime;

/**
 * Cuerpo ya validado de PATCH /api/historial/{id} (schema {@code HistorialPatch}, merge patch). Un componente
 * {@code null} significa "no enviado" (no cambia); un {@link ValorPatch} con valor {@code null} significa "poner
 * null", lo cual solo ocurre con {@code leccionId} (desvincular de la lección).
 */
public record HistorialPatch(ValorPatch<Integer> leccionId, ValorPatch<String> nombreLeccion,
                             ValorPatch<LocalDateTime> horaApertura, ValorPatch<EstadoHistorial> estado) {
}
