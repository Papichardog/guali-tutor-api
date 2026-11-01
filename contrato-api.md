# Contrato de la API REST — Guali Tutor (Fase 2)

Resumen de revisión de `openapi.json` (OpenAPI 3.0.3, versión 1.0.0). **La fuente de verdad es `openapi.json`**; este documento se
generó a partir de él, así que no puede contradecirlo.

## 1. Alcance

```
Swing (Fase 1) ──HTTPS/JSON──▶ API REST (Fase 2) ──JPA──▶ MariaDB
```

- Servidor de desarrollo: `https://localhost:8443` (certificado autofirmado). Sin autenticación (el enunciado no la exige).
- La API **solo administra datos**: configuración, lecciones (con sus imágenes) e historial. Compilar y ejecutar (guali-dev, JDK) siguen en Swing.
- El front end **no accede a MariaDB**; el backend sí, vía JPA/Hibernate (por eso lleva el driver).

## 2. Los 17 endpoints

| # | Método | Ruta | Operación | Éxito | Errores |
|---|---|---|---|---|---|
| 1 | `GET` | `/api/configuracion` | Listar las configuraciones | 200 | 500 |
| 2 | `POST` | `/api/configuracion` | Crear una configuración | 201 | 400, 409, 500 |
| 3 | `GET` | `/api/configuracion/{id}` | Obtener una configuración | 200 | 400, 404, 500 |
| 4 | `PUT` | `/api/configuracion/{id}` | Reemplazar (o crear) una configuración | 200 / 201 | 400, 500 |
| 5 | `PATCH` | `/api/configuracion/{id}` | Actualizar parcialmente una configuración | 200 | 400, 404, 500 |
| 6 | `DELETE` | `/api/configuracion/{id}` | Eliminar una configuración | 204 | 400, 404, 500 |
| 7 | `GET` | `/api/lecciones` | Listar las lecciones (resumen) | 200 | 500 |
| 8 | `POST` | `/api/lecciones` | Crear una lección (Importar Lección a BD) | 201 | 400, 409, 500 |
| 9 | `GET` | `/api/lecciones/{id}` | Obtener una lección completa | 200 | 400, 404, 500 |
| 10 | `PUT` | `/api/lecciones/{id}` | Reemplazar una lección | 200 | 400, 404, 409, 500 |
| 11 | `DELETE` | `/api/lecciones/{id}` | Eliminar una lección | 204 | 400, 404, 500 |
| 12 | `GET` | `/api/historial` | Listar el historial | 200 | 500 |
| 13 | `POST` | `/api/historial` | Registrar un intento (apertura de lección) | 201 | 400, 500 |
| 14 | `GET` | `/api/historial/{id}` | Obtener un registro del historial | 200 | 400, 404, 500 |
| 15 | `PUT` | `/api/historial/{id}` | Reemplazar un registro del historial | 200 | 400, 404, 409, 500 |
| 16 | `PATCH` | `/api/historial/{id}` | Actualizar parcialmente un registro (p. ej. marcar Completada) | 200 | 400, 404, 409, 500 |
| 17 | `DELETE` | `/api/historial/{id}` | Eliminar un registro del historial | 204 | 400, 404, 500 |

Las lecciones **no tienen PATCH** (se cambian con PUT). Las imágenes **no tienen endpoints propios**: viajan dentro del recurso lección.

## 3. PUT vs PATCH

| | PUT | PATCH |
|---|---|---|
| Semántica | Reemplazo **completo** | Actualización **parcial** (JSON Merge Patch, RFC 7396) |
| Campos | Todos los obligatorios; los anulables se envían como `null` si no hay valor | Solo los que cambian |
| Campo ausente | No existe el PUT parcial: es un 400 | **No cambia** |
| Campo `null` | Valor vacío (solo en anulables) | **Borra** el valor (solo en anulables: `jdkPath`, `leccionesRootPath`, `leccionId`) |
| Cuerpo `{}` | 400 | 400 |
| Recurso inexistente | `configuracion`: **lo crea (201)**. `lecciones`, `historial`: 404 | 404 |
| Dónde existe | configuración, lecciones, historial | configuración, historial |

Por qué: PUT de configuración con solo el JDK borraría la carpeta de lecciones; con PATCH configurar una nunca pisa la otra. Y
«marcar Completada» es `PATCH /api/historial/{id}` con `{"estado":"Completada"}`, no una operación aparte.

## 4. Errores

Todas las respuestas de error tienen la misma estructura (`Error`):

```json
{
  "status": 409,
  "error": "CONFLICT",
  "codigo": "LECCION_DUPLICADA",
  "message": "Ya existe una lección con el nombre 'leccion-01'.",
  "path": "/api/lecciones",
  "timestamp": "2026-10-14T21:30:05"
}
```

| status | error | codigo | Cuándo |
|---|---|---|---|
| 400 | `BAD_REQUEST` | `VALIDACION_FALLIDA` | Campo inválido (trae `errors[]` con un elemento por campo) |
| 400 | `BAD_REQUEST` | `CUERPO_INVALIDO` | JSON malformado o propiedad desconocida |
| 400 | `BAD_REQUEST` | `PARAMETRO_INVALIDO` | El `id` de la ruta no es un entero |
| 400 | `BAD_REQUEST` | `REFERENCIA_INVALIDA` | `leccionId` no existe en `lecciones` |
| 404 | `NOT_FOUND` | `RECURSO_NO_ENCONTRADO` | No existe el recurso con ese id |
| 409 | `CONFLICT` | `LECCION_DUPLICADA` | Nombre de lección ya usado |
| 409 | `CONFLICT` | `CONFIGURACION_DUPLICADA` | POST de configuración con un id existente |
| 409 | `CONFLICT` | `ESTADO_NO_REGRESIVO` | Intentar pasar un historial de Completada a Pendiente |
| 500 | `INTERNAL_SERVER_ERROR` | `ERROR_INTERNO` | Fallo inesperado (sin detalles internos) |

El cliente decide con `codigo`, **no** interpretando `message`.

## 5. Reglas de validación

Valen para todo cuerpo de entrada; los límites son **decisiones de diseño**, no exigencias del enunciado.

**General:** `id` entero ≥ 1. Se rechazan propiedades desconocidas (400). Fechas: `yyyy-MM-ddTHH:mm:ss`, sin zona horaria.

| Recurso | Campo | Regla |
|---|---|---|
| Configuración | `id` (solo POST) | entero ≥ 1; si ya existe, 409 |
| | `jdkPath`, `leccionesRootPath` | texto ≤ 500 o `null`. La API **no** comprueba que la ruta exista (es de la máquina del cliente) |
| | PATCH | al menos un campo |
| Lección | `nombre` | 1–255 caracteres, no en blanco, **único** (ignora mayúsculas y acentos) |
| | `contenidoMarkdown` | texto ≤ 1.000.000 caracteres (puede ser vacío) |
| | `objetivo` | `COMPILAR_CON_ERROR`, `COMPILAR_EXITOSO`, `EJECUTAR_CON_ERROR` o `EJECUTAR_EXITOSO` |
| | `imagenes` | ≤ 20. Opcional en POST (por defecto `[]`); **obligatorio** en PUT (`[]` borra todas) |
| Imagen | `nombre` | 1–255, sin `/` ni `\`, único dentro de la lección (el nombre que referencia el Markdown) |
| | `mimeType` | `image/png`, `image/jpeg`, `image/gif` o `image/webp` |
| | `contenidoBase64` | base64 válido, ≤ 7.000.000 caracteres (≈ 5 MB) |
| Historial | `nombreLeccion` | 1–255, no en blanco |
| | `leccionId` | entero ≥ 1 o `null`; si no es `null`, debe existir (si no, 400 `REFERENCIA_INVALIDA`) |
| | `horaApertura` | fecha-hora local; por defecto, la hora del servidor |
| | `estado` | `Pendiente` (por defecto) o `Completada` |

## 6. Duplicados y reglas de negocio

- **Lecciones duplicadas:** `POST /api/lecciones` con un `nombre` ya existente → **409 `LECCION_DUPLICADA`** y **no se modifica nada** (no hay
  sobrescritura). La unicidad la da la colación `utf8mb4_general_ci`: `Lección-01` y `leccion-01` son el mismo nombre. En `PUT`, dejar el
  nombre propio no es conflicto; usar el de **otra** lección, sí.
- **Eliminar una lección:** borra sus imágenes (`ON DELETE CASCADE`) y **conserva el historial**: los registros quedan con `leccionId = null`
  (`ON DELETE SET NULL`) y mantienen `nombreLeccion` y `estado`.
- **Historial:** una fila `Completada` **no vuelve a `Pendiente`** (409 `ESTADO_NO_REGRESIVO`, en PUT y PATCH). Marcar `Completada` algo
  ya completado es **idempotente** (200, sin cambios).

## 7. Ejemplos

**Importar una lección con imagen** — `POST /api/lecciones`

```json
{
  "nombre": "leccion-01",
  "contenidoMarkdown": "# Hola mundo\n\nEscribe este programa:\n\n![diagrama](img-lec-1.png)\n",
  "objetivo": "COMPILAR_EXITOSO",
  "imagenes": [
    {
      "nombre": "img-lec-1.png",
      "mimeType": "image/png",
      "contenidoBase64": "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg=="
    }
  ]
}
```

Respuesta `201 Created` (cabecera `Location: https://localhost:8443/api/lecciones/1`):

```json
{
  "id": 1,
  "nombre": "leccion-01",
  "contenidoMarkdown": "# Hola mundo\n\nEscribe este programa:\n\n![diagrama](img-lec-1.png)\n",
  "objetivo": "COMPILAR_EXITOSO",
  "fechaImportacion": "2026-10-14T20:15:00",
  "imagenes": [
    {
      "nombre": "img-lec-1.png",
      "mimeType": "image/png",
      "contenidoBase64": "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg=="
    }
  ]
}
```

**Listado liviano** — `GET /api/lecciones` (sin Markdown ni imágenes):

```json
[
  {
    "id": 1,
    "nombre": "leccion-01",
    "objetivo": "COMPILAR_EXITOSO",
    "fechaImportacion": "2026-10-14T20:15:00"
  },
  {
    "id": 2,
    "nombre": "leccion-02-errores",
    "objetivo": "EJECUTAR_CON_ERROR",
    "fechaImportacion": "2026-10-14T20:20:41"
  }
]
```

**Cambiar solo el JDK** — `PATCH /api/configuracion/1`:

```json
{
  "jdkPath": "/Library/Java/JavaVirtualMachines/temurin-25.jdk/Contents/Home"
}
```

Respuesta `200 OK`: la carpeta de lecciones **se conserva**.

```json
{
  "id": 1,
  "jdkPath": "/Library/Java/JavaVirtualMachines/temurin-25.jdk/Contents/Home",
  "leccionesRootPath": "/Users/pablo/lecciones"
}
```

**Registrar una apertura** — `POST /api/historial` → `201`:

```json
{
  "nombreLeccion": "leccion-01"
}
```
```json
{
  "id": 15,
  "leccionId": null,
  "nombreLeccion": "leccion-01",
  "horaApertura": "2026-10-14T21:30:05",
  "estado": "Pendiente"
}
```

**Marcar Completada** — `PATCH /api/historial/15` → `200`:

```json
{
  "estado": "Completada"
}
```

**Intentar volver a Pendiente** — `409`:

```json
{
  "status": 409,
  "error": "CONFLICT",
  "codigo": "ESTADO_NO_REGRESIVO",
  "message": "Un registro Completada no puede volver a Pendiente.",
  "path": "/api/historial/15",
  "timestamp": "2026-10-14T21:30:05"
}
```

**Validación fallida** — `POST /api/lecciones` → `400`:

```json
{
  "status": 400,
  "error": "BAD_REQUEST",
  "codigo": "VALIDACION_FALLIDA",
  "message": "Hay campos inválidos en la solicitud.",
  "path": "/api/lecciones",
  "timestamp": "2026-10-14T21:30:05",
  "errors": [
    {
      "campo": "objetivo",
      "mensaje": "debe ser uno de: COMPILAR_CON_ERROR, COMPILAR_EXITOSO, EJECUTAR_CON_ERROR, EJECUTAR_EXITOSO"
    },
    {
      "campo": "imagenes[0].mimeType",
      "mensaje": "debe ser uno de: image/png, image/jpeg, image/gif, image/webp"
    }
  ]
}
```

## 8. Equivalencias con la Fase 1 (guía para migrar el front end)

| Fase 1 (JDBC) | Fase 2 (REST) | Cómo se interpreta la respuesta |
|---|---|---|
| `ConfiguracionDAO.obtenerRutaJdk` / `obtenerRutaLecciones` | `GET /api/configuracion/1` | 404 → `null` (aún sin configurar) |
| `guardarRutaJdk` / `guardarRutaLecciones` | `PATCH /api/configuracion/1` con solo ese campo | 404 → crear con `POST` o `PUT` |
| `LeccionDAO.insertar` | `POST /api/lecciones` | 201 → `true`; 409 `LECCION_DUPLICADA` → `false` |
| `listarResumen` | `GET /api/lecciones` | |
| `obtenerPorId` | `GET /api/lecciones/{id}` | 404 → `null` |
| `eliminarPorId` | `DELETE /api/lecciones/{id}` | 204 → `true`; 404 → `false` |
| `HistorialDAO.insertarIntento` | `POST /api/historial` | devuelve el `id` generado |
| `marcarCompletada` | `PATCH /api/historial/{id}` con `{"estado":"Completada"}` | 200 → `true`; 404 → `false` |
| `listarTodos` | `GET /api/historial` | |

Un matiz: en la Fase 1 `marcarCompletada` devolvía `false` si la fila ya estaba Completada; ahora esa situación responde 200 y se
interpreta como `true`, que es lo correcto (el intento está completado).

## 9. Esquema SQL nuevo (aprobado, aún no ejecutado)

La única tabla nueva. Usa el mismo motor, juego de caracteres y colación que `lecciones` e `historial` (`InnoDB`, `utf8mb4`,
`utf8mb4_general_ci`, verificados con `SHOW CREATE TABLE` en MariaDB 12.3.3). Por eso el `nombre` de una imagen es único dentro de la
lección **sin distinguir mayúsculas ni acentos**, igual que `lecciones.nombre`. Es el contenido de `crear-leccion-imagenes.sql`.

```sql
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
```

## 10. Decisiones de diseño para revisar

1. **HTTPS por defecto** en `https://localhost:8443`, con certificado autofirmado de desarrollo; el cliente confiará en él sin desactivar la verificación.
2. **PUT puede crear solo en configuración**, porque su `id` lo elige el cliente (tabla sin AUTO_INCREMENT; la app usa `id = 1`).
3. **PATCH = merge patch** (ausente no cambia, `null` borra). El backend deberá distinguir «ausente» de «null» al leer el JSON.
4. **Propiedades desconocidas → 400.** Requiere configurar Jackson para rechazarlas (por defecto Spring las ignora).
5. **Listado de lecciones sin Markdown ni imágenes**; el detalle sí las incluye.
6. **Imágenes dentro del recurso lección** en base64, con límites (≤ 20 por lección, ≈ 5 MB cada una, PNG/JPEG/GIF/WebP) y solo en la carpeta de la lección (sin subcarpetas).
7. **Fechas sin zona horaria** (`DATETIME` del servidor), no `date-time` RFC 3339 estricto.
8. **`leccionId` inexistente → 400 `REFERENCIA_INVALIDA`** (no 404, que se reserva para el recurso de la URL).
9. **Sin paginación** en los listados.
10. **Sin autenticación**: el enunciado no la pide.