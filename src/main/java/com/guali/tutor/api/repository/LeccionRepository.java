package com.guali.tutor.api.repository;

import com.guali.tutor.api.dao.Leccion;
import com.guali.tutor.api.dto.LeccionResumen;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/** Acceso a la tabla {@code lecciones} (y, por cascada, a sus imágenes). */
public interface LeccionRepository extends JpaRepository<Leccion, Integer> {

    /**
     * ¿Ya hay una lección con este nombre? La comparación la hace MariaDB con la colación de la columna
     * ({@code utf8mb4_general_ci}), así que ignora mayúsculas y acentos, igual que el índice único.
     */
    boolean existsByNombreIgnoreCase(String nombre);

    /** Igual que {@link #existsByNombreIgnoreCase}, pero sin contar la propia lección (para el PUT). */
    boolean existsByNombreIgnoreCaseAndIdNot(String nombre, Integer id);

    /**
     * Listado liviano: solo las cuatro columnas del resumen, sin {@code contenido_markdown} ni imágenes, por
     * nombre ascendente (como {@code LeccionDAO.listarResumen} en la Fase 1).
     */
    @Query("select new com.guali.tutor.api.dto.LeccionResumen(l.id, l.nombre, l.objetivo, l.fechaImportacion) "
            + "from Leccion l order by l.nombre asc")
    List<LeccionResumen> listarResumen();
}
