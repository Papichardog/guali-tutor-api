package com.guali.tutor.api.dto;

import java.time.LocalDateTime;

/**
 * Elemento del listado (schema {@code LeccionResumen}): sin Markdown ni imágenes. La consulta del repositorio
 * lo construye directamente desde la BD con el segundo constructor, que da formato a la fecha.
 */
public record LeccionResumen(Integer id, String nombre, String objetivo, String fechaImportacion) {

    public LeccionResumen(Integer id, String nombre, String objetivo, LocalDateTime fechaImportacion) {
        this(id, nombre, objetivo, FechaHora.formatear(fechaImportacion));
    }
}
