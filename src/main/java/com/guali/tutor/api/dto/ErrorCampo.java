package com.guali.tutor.api.dto;

/** Un campo inválido dentro de un error de validación (schema {@code ErrorCampo} del contrato). */
public record ErrorCampo(String campo, String mensaje) {
}
