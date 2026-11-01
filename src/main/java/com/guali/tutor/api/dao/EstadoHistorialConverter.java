package com.guali.tutor.api.dao;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Convierte {@link EstadoHistorial} al texto que guarda la columna ENUM ("Pendiente" / "Completada") y de vuelta. */
@Converter
public class EstadoHistorialConverter implements AttributeConverter<EstadoHistorial, String> {

    @Override
    public String convertToDatabaseColumn(EstadoHistorial estado) {
        return estado == null ? null : estado.getTexto();
    }

    @Override
    public EstadoHistorial convertToEntityAttribute(String texto) {
        return texto == null ? null : EstadoHistorial.desdeTexto(texto);
    }
}
