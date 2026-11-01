package com.guali.tutor.api.dto;

/** Imagen de una lección en las respuestas (schema {@code LeccionImagen}): el binario va en base64. */
public record ImagenResponse(String nombre, String mimeType, String contenidoBase64) {
}
