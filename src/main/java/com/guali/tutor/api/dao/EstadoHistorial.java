package com.guali.tutor.api.dao;

import java.util.Arrays;
import java.util.List;

/**
 * Estado de un intento. En la BD es un {@code ENUM('Completada','Pendiente')} con esos textos exactos, no con los
 * nombres de esta enumeración, por eso se guarda con {@link EstadoHistorialConverter} (y no con
 * {@code @Enumerated}, que escribiría PENDIENTE y MariaDB lo rechazaría).
 */
public enum EstadoHistorial {

    PENDIENTE("Pendiente"),
    COMPLETADA("Completada");

    private final String texto;

    EstadoHistorial(String texto) {
        this.texto = texto;
    }

    /** El texto tal como lo usan la BD y el contrato: "Pendiente" o "Completada". */
    public String getTexto() {
        return texto;
    }

    /** Los textos válidos, en el orden del contrato (para los mensajes de validación). */
    public static List<String> textos() {
        return Arrays.stream(values()).map(EstadoHistorial::getTexto).toList();
    }

    /** @throws IllegalArgumentException si el texto no es uno de los dos estados */
    public static EstadoHistorial desdeTexto(String texto) {
        for (EstadoHistorial estado : values()) {
            if (estado.texto.equals(texto)) {
                return estado;
            }
        }
        throw new IllegalArgumentException("Estado de historial desconocido: " + texto);
    }
}
