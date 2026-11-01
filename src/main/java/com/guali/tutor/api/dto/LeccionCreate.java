package com.guali.tutor.api.dto;

import java.util.List;

/** Cuerpo ya validado de POST /api/lecciones (schema {@code LeccionCreate}). {@code imagenes} nunca es null. */
public record LeccionCreate(String nombre, String contenidoMarkdown, String objetivo, List<ImagenEntrada> imagenes) {
}
