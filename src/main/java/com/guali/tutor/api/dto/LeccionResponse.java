package com.guali.tutor.api.dto;

import java.util.List;

/** Detalle completo de una lección en las respuestas (schema {@code Leccion}), con su Markdown y sus imágenes. */
public record LeccionResponse(Integer id, String nombre, String contenidoMarkdown, String objetivo,
                              String fechaImportacion, List<ImagenResponse> imagenes) {
}
