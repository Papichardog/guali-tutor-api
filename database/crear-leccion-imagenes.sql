-- ============================================================================
-- Guali Tutor - Fase 2
-- Nueva tabla: leccion_imagenes                                     (versión 2)
--
-- Guarda las imágenes de cada lección (las que el Markdown referencia, p. ej.
-- img-lec-1.png) para que una lección importada a la BD se vea completa al
-- seleccionarla. La API las expone DENTRO del recurso lección, sin endpoints propios.
--
-- Esquema real tomado de SHOW CREATE TABLE en MariaDB 12.3.3:
--   lecciones.id  = INT(11) con signo, AUTO_INCREMENT, clave primaria
--   lecciones e historial = InnoDB, utf8mb4, colación utf8mb4_general_ci
--
-- Solo CREA una tabla nueva. No modifica lecciones, historial ni configuracion.
-- Se usa CREATE TABLE sin IF NOT EXISTS a propósito: si ya existiera una tabla
-- con ese nombre, el script debe fallar en lugar de omitirse en silencio.
-- ============================================================================

USE guali_tutor;

CREATE TABLE leccion_imagenes (
    id          INT           NOT NULL AUTO_INCREMENT,
    leccion_id  INT           NOT NULL,
    nombre      VARCHAR(255)  NOT NULL,
    mime_type   VARCHAR(50)   NOT NULL,
    contenido   LONGBLOB      NOT NULL,

    PRIMARY KEY (id),

    -- Una lección no puede tener dos imágenes con el mismo nombre. Como en
    -- lecciones.nombre, la comparación ignora mayúsculas y acentos (colación de la
    -- tabla), lo que además evita dos nombres que solo difieren en mayúsculas y que
    -- chocarían al escribirse como archivos en macOS o Windows.
    -- Este índice también sirve de índice para la clave foránea (empieza por leccion_id).
    UNIQUE KEY uq_leccion_imagenes_leccion_nombre (leccion_id, nombre),

    -- Si se elimina la lección, se eliminan sus imágenes.
    CONSTRAINT fk_leccion_imagenes_leccion
        FOREIGN KEY (leccion_id) REFERENCES lecciones (id)
        ON DELETE CASCADE
        ON UPDATE CASCADE,

    -- Reglas del contrato OpenAPI, reforzadas en la BD como última barrera:
    -- el nombre es un nombre de archivo (sin '/' ni '\'), el tipo es uno de los
    -- cuatro permitidos y la imagen no está vacía.
    CONSTRAINT ck_leccion_imagenes_nombre
        CHECK (CHAR_LENGTH(nombre) >= 1
               AND LOCATE('/', nombre) = 0
               AND LOCATE(CHAR(92 USING utf8mb4), nombre) = 0),
    CONSTRAINT ck_leccion_imagenes_mime
        CHECK (mime_type IN ('image/png', 'image/jpeg', 'image/gif', 'image/webp')),
    CONSTRAINT ck_leccion_imagenes_contenido
        CHECK (OCTET_LENGTH(contenido) > 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci;

-- ----------------------------------------------------------------------------
-- Para deshacer (solo borra la tabla nueva; nada más depende de ella):
--   DROP TABLE leccion_imagenes;
-- ----------------------------------------------------------------------------
