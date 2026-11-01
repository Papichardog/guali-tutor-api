package com.guali.tutor.api.repository;

import com.guali.tutor.api.dao.Historial;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Acceso a la tabla {@code historial}. */
public interface HistorialRepository extends JpaRepository<Historial, Integer> {

    /**
     * Todo el historial, del intento más antiguo al más reciente: por {@code hora_apertura} ascendente y, para la
     * misma hora, por {@code id} ascendente (el mismo orden que {@code HistorialDAO.listarTodos} en la Fase 1).
     */
    List<Historial> findAllByOrderByHoraAperturaAscIdAsc();
}
