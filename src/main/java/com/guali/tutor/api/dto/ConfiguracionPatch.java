package com.guali.tutor.api.dto;

/**
 * Cuerpo ya validado de PATCH /api/configuracion/{id} (schema {@code ConfiguracionPatch}). Un componente
 * {@code null} significa "no enviado"; ver {@link ValorPatch}.
 */
public record ConfiguracionPatch(ValorPatch<String> jdkPath, ValorPatch<String> leccionesRootPath) {
}
