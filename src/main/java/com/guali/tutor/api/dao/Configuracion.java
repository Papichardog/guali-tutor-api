package com.guali.tutor.api.dao;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Fila de la tabla existente {@code configuracion} (esquema de la Fase 1, sin cambios):
 * <pre>
 *   id                  INT PRIMARY KEY   (sin AUTO_INCREMENT: lo elige el cliente)
 *   jdk_path            VARCHAR(500) NULL
 *   lecciones_root_path VARCHAR(500) NULL
 * </pre>
 */
@Entity
@Table(name = "configuracion")
public class Configuracion {

    @Id
    @Column(name = "id")
    private Integer id;

    @Column(name = "jdk_path", length = 500)
    private String jdkPath;

    @Column(name = "lecciones_root_path", length = 500)
    private String leccionesRootPath;

    /** Exigido por JPA. */
    protected Configuracion() {
    }

    public Configuracion(Integer id, String jdkPath, String leccionesRootPath) {
        this.id = id;
        this.jdkPath = jdkPath;
        this.leccionesRootPath = leccionesRootPath;
    }

    public Integer getId() {
        return id;
    }

    public String getJdkPath() {
        return jdkPath;
    }

    public void setJdkPath(String jdkPath) {
        this.jdkPath = jdkPath;
    }

    public String getLeccionesRootPath() {
        return leccionesRootPath;
    }

    public void setLeccionesRootPath(String leccionesRootPath) {
        this.leccionesRootPath = leccionesRootPath;
    }
}
