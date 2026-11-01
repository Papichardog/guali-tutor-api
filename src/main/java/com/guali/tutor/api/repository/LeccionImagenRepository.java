package com.guali.tutor.api.repository;

import com.guali.tutor.api.dao.LeccionImagen;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Acceso a la tabla {@code leccion_imagenes}. Las imágenes se guardan, reemplazan y borran a través de
 * {@link com.guali.tutor.api.dao.Leccion} (cascada y orphanRemoval), así que ningún servicio la usa todavía;
 * queda disponible para consultas directas.
 */
public interface LeccionImagenRepository extends JpaRepository<LeccionImagen, Integer> {
}
