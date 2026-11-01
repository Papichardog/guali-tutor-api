package com.guali.tutor.api.dto;

import com.guali.tutor.api.dao.EstadoHistorial;

import java.time.LocalDateTime;

/**
 * Cuerpo ya validado de PUT /api/historial/{id}: reemplazo completo (schema {@code HistorialUpdate}). Todos los
 * campos vienen informados; solo {@code leccionId} puede ser {@code null} (sin lección).
 */
public record HistorialUpdate(Integer leccionId, String nombreLeccion, LocalDateTime horaApertura,
                              EstadoHistorial estado) {
}
