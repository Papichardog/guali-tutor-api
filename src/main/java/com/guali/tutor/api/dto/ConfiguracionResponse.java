package com.guali.tutor.api.dto;

/** Representación de una configuración en las respuestas (schema {@code Configuracion}). */
public record ConfiguracionResponse(Integer id, String jdkPath, String leccionesRootPath) {
}
