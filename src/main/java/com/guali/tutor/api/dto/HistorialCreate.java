package com.guali.tutor.api.dto;

import com.guali.tutor.api.dao.EstadoHistorial;

import java.time.LocalDateTime;

/**
 * Cuerpo ya validado de POST /api/historial (schema {@code HistorialCreate}). {@code horaApertura} y {@code estado}
 * son {@code null} cuando el cliente no los envió: el servidor usa entonces la hora actual y "Pendiente".
 */
public record HistorialCreate(Integer leccionId, String nombreLeccion, LocalDateTime horaApertura,
                              EstadoHistorial estado) {
}
