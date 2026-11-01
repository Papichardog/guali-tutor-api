package com.guali.tutor.api.dto;

/** Cuerpo ya validado de POST /api/configuracion (schema {@code ConfiguracionCreate}). */
public record ConfiguracionCreate(int id, String jdkPath, String leccionesRootPath) {
}
