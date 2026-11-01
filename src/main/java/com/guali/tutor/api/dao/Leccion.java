package com.guali.tutor.api.dao;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Fila de la tabla existente {@code lecciones} (esquema de la Fase 1, sin cambios):
 * <pre>
 *   id                 INT AUTO_INCREMENT PRIMARY KEY
 *   nombre             VARCHAR(255) NOT NULL UNIQUE      (utf8mb4_general_ci: ignora mayúsculas y acentos)
 *   contenido_markdown LONGTEXT NOT NULL
 *   objetivo           ENUM('COMPILAR_CON_ERROR','COMPILAR_EXITOSO','EJECUTAR_CON_ERROR','EJECUTAR_EXITOSO') NOT NULL
 *   fecha_importacion  DATETIME NOT NULL DEFAULT current_timestamp()
 * </pre>
 * Sus imágenes viven en la tabla {@code leccion_imagenes} y forman parte de esta entidad: se guardan, se
 * reemplazan y se borran junto con la lección.
 */
@Entity
@Table(name = "lecciones")
public class Leccion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "nombre", nullable = false, length = 255)
    private String nombre;

    @Column(name = "contenido_markdown", nullable = false, columnDefinition = "longtext")
    private String contenidoMarkdown;

    /** Uno de los cuatro objetivos del contrato; la columna es un ENUM de MariaDB y aquí se maneja como texto. */
    @Column(name = "objetivo", nullable = false, length = 30)
    private String objetivo;

    /** Se asigna al crear y no se vuelve a escribir nunca (un PUT no cambia la fecha de importación). */
    @Column(name = "fecha_importacion", nullable = false, updatable = false)
    private LocalDateTime fechaImportacion;

    /**
     * Imágenes de la lección. {@code cascade = ALL} las guarda y borra con la lección; {@code orphanRemoval}
     * borra de la BD las que se quiten de esta lista. Carga perezosa: listar o leer una lección sin tocar
     * esta colección no descarga ningún binario. No se debe sustituir la lista por otra: se modifica en su lugar.
     */
    @OneToMany(mappedBy = "leccion", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("id ASC")
    private List<LeccionImagen> imagenes = new ArrayList<>();

    /** Exigido por JPA. */
    protected Leccion() {
    }

    public Leccion(String nombre, String contenidoMarkdown, String objetivo) {
        this.nombre = nombre;
        this.contenidoMarkdown = contenidoMarkdown;
        this.objetivo = objetivo;
        // Se fija aquí (a segundos, como guarda MariaDB un DATETIME) para que lo que la API responde en el POST
        // sea exactamente lo que queda en la BD, sin tener que releer la fila.
        this.fechaImportacion = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
    }

    /** Añade una imagen y la enlaza a esta lección (los dos lados de la relación). */
    public void agregarImagen(LeccionImagen imagen) {
        imagen.setLeccion(this);
        imagenes.add(imagen);
    }

    public Integer getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getContenidoMarkdown() {
        return contenidoMarkdown;
    }

    public void setContenidoMarkdown(String contenidoMarkdown) {
        this.contenidoMarkdown = contenidoMarkdown;
    }

    public String getObjetivo() {
        return objetivo;
    }

    public void setObjetivo(String objetivo) {
        this.objetivo = objetivo;
    }

    public LocalDateTime getFechaImportacion() {
        return fechaImportacion;
    }

    /** La lista real (no una copia): necesaria para que orphanRemoval detecte las imágenes quitadas. */
    public List<LeccionImagen> getImagenes() {
        return imagenes;
    }
}
