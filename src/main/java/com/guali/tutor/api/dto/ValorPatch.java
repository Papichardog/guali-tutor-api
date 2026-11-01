package com.guali.tutor.api.dto;

/**
 * Valor de un campo en un PATCH (merge patch). Distingue tres situaciones que un simple {@code String}
 * no puede distinguir:
 * <ul>
 *   <li>referencia {@code null}: el campo vino <b>ausente</b> en el JSON, así que no se modifica;</li>
 *   <li>{@code new ValorPatch<>(null)}: el campo vino con {@code null}, así que se <b>borra</b>;</li>
 *   <li>{@code new ValorPatch<>("x")}: el campo vino con valor, así que se <b>actualiza</b>.</li>
 * </ul>
 */
public record ValorPatch<T>(T valor) {
}
