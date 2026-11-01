package com.guali.tutor.api.dto;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Formato de fecha-hora del contrato: ISO-8601 local, sin zona horaria y siempre con segundos. */
public final class FechaHora {

    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private FechaHora() {
    }

    public static String formatear(LocalDateTime fecha) {
        return fecha == null ? null : fecha.format(FORMATO);
    }
}
