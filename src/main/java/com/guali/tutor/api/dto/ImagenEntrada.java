package com.guali.tutor.api.dto;

/** Imagen ya validada y decodificada de un cuerpo de POST/PUT: el contenido ya son los bytes, no base64. */
public record ImagenEntrada(String nombre, String mimeType, byte[] contenido) {
}
