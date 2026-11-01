package com.guali.tutor.api.dao;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Fila de la tabla existente {@code historial} (esquema de la Fase 1, sin cambios):
 * <pre>
 *   id             INT AUTO_INCREMENT PRIMARY KEY
 *   leccion_id     INT NULL -> lecciones(id) ON DELETE SET NULL ON UPDATE CASCADE
 *   nombre_leccion VARCHAR(255) NOT NULL
 *   hora_apertura  DATETIME NOT NULL DEFAULT current_timestamp()
 *   estado         ENUM('Completada','Pendiente') NOT NULL DEFAULT 'Pendiente'
 * </pre>
 */
@Entity
@Table(name = "historial")
public class Historial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    /**
     * Lección que se abrió, o {@code null} si era una lección local (que no existe en la BD) o si la lección se
     * eliminó después. Es una referencia perezosa y SIN cascada: borrar un registro de historial nunca borra la
     * lección. El borrado inverso (eliminar una lección) lo resuelve la propia BD con {@code ON DELETE SET NULL}:
     * el registro se conserva con {@code leccion_id = NULL}, sin que Hibernate intervenga.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "leccion_id")
    private Leccion leccion;

    /** Copia del nombre de la lección: se conserva aunque la lección se elimine. */
    @Column(name = "nombre_leccion", nullable = false, length = 255)
    private String nombreLeccion;

    @Column(name = "hora_apertura", nullable = false)
    private LocalDateTime horaApertura;

    @Convert(converter = EstadoHistorialConverter.class)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoHistorial estado;

    /** Exigido por JPA. */
    protected Historial() {
    }

    public Historial(Leccion leccion, String nombreLeccion, LocalDateTime horaApertura, EstadoHistorial estado) {
        this.leccion = leccion;
        this.nombreLeccion = nombreLeccion;
        this.horaApertura = horaApertura;
        this.estado = estado;
    }

    public Integer getId() {
        return id;
    }

    public Leccion getLeccion() {
        return leccion;
    }

    public void setLeccion(Leccion leccion) {
        this.leccion = leccion;
    }

    public String getNombreLeccion() {
        return nombreLeccion;
    }

    public void setNombreLeccion(String nombreLeccion) {
        this.nombreLeccion = nombreLeccion;
    }

    public LocalDateTime getHoraApertura() {
        return horaApertura;
    }

    public void setHoraApertura(LocalDateTime horaApertura) {
        this.horaApertura = horaApertura;
    }

    public EstadoHistorial getEstado() {
        return estado;
    }

    public void setEstado(EstadoHistorial estado) {
        this.estado = estado;
    }
}
