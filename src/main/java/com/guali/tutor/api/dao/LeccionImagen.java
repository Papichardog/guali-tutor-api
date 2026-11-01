package com.guali.tutor.api.dao;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Fila de la tabla nueva {@code leccion_imagenes}:
 * <pre>
 *   id          INT AUTO_INCREMENT PRIMARY KEY
 *   leccion_id  INT NOT NULL  -> lecciones(id) ON DELETE CASCADE ON UPDATE CASCADE
 *   nombre      VARCHAR(255) NOT NULL        (único por lección, sin distinguir mayúsculas ni acentos)
 *   mime_type   VARCHAR(50) NOT NULL         (image/png, image/jpeg, image/gif, image/webp)
 *   contenido   LONGBLOB NOT NULL
 * </pre>
 * No tiene endpoints propios: se maneja siempre a través de su {@link Leccion}.
 */
@Entity
@Table(name = "leccion_imagenes")
public class LeccionImagen {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "leccion_id", nullable = false)
    private Leccion leccion;

    /** Nombre de archivo tal como lo referencia el Markdown (p. ej. img-lec-1.png). */
    @Column(name = "nombre", nullable = false, length = 255)
    private String nombre;

    @Column(name = "mime_type", nullable = false, length = 50)
    private String mimeType;

    @Lob
    @Column(name = "contenido", nullable = false)
    private byte[] contenido;

    /** Exigido por JPA. */
    protected LeccionImagen() {
    }

    public LeccionImagen(String nombre, String mimeType, byte[] contenido) {
        this.nombre = nombre;
        this.mimeType = mimeType;
        this.contenido = contenido;
    }

    public Integer getId() {
        return id;
    }

    public Leccion getLeccion() {
        return leccion;
    }

    /** Solo la usa {@link Leccion#agregarImagen(LeccionImagen)}, que mantiene sincronizados los dos lados. */
    void setLeccion(Leccion leccion) {
        this.leccion = leccion;
    }

    public String getNombre() {
        return nombre;
    }

    public String getMimeType() {
        return mimeType;
    }

    public byte[] getContenido() {
        return contenido;
    }
}
