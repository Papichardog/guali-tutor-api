package com.guali.tutor.api.repository;

import com.guali.tutor.api.dao.Configuracion;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Acceso a la tabla {@code configuracion}. Spring Data genera la implementación;
 * findAll, findById, existsById, save y delete bastan para el CRUD del contrato.
 */
public interface ConfiguracionRepository extends JpaRepository<Configuracion, Integer> {
}
