package com.guali.tutor.api.dto;

/** Cuerpo ya validado de PUT /api/configuracion/{id}: reemplazo completo (schema {@code ConfiguracionUpdate}). */
public record ConfiguracionUpdate(String jdkPath, String leccionesRootPath) {
}
