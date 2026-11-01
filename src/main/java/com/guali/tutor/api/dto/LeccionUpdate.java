package com.guali.tutor.api.dto;

import java.util.List;

/**
 * Cuerpo ya validado de PUT /api/lecciones/{id}: reemplazo completo (schema {@code LeccionUpdate}). El conjunto de
 * {@code imagenes} sustituye al anterior; una lista vacía las elimina todas.
 */
public record LeccionUpdate(String nombre, String contenidoMarkdown, String objetivo, List<ImagenEntrada> imagenes) {
}
