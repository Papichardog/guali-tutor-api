package com.guali.tutor.api.dto;

import com.guali.tutor.api.exception.ApiException;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Convierte y valida los cuerpos JSON de POST y PUT de /api/lecciones según el contrato OpenAPI, con el mismo
 * enfoque que {@link ConfiguracionRequestParser}: los cuerpos llegan como {@code Map} para poder distinguir un
 * campo ausente de uno con null y rechazar propiedades desconocidas sin depender de la configuración de Jackson.
 * Se validan todos los campos y se informan todos los errores a la vez. No depende de Spring.
 */
public final class LeccionRequestParser {

    public static final int MAX_NOMBRE = 255;
    public static final int MAX_MARKDOWN = 1_000_000;
    public static final int MAX_IMAGENES = 20;
    /** ≈ 5 MB ya decodificados. Se comprueba ANTES de decodificar, para no gastar memoria con cuerpos enormes. */
    public static final int MAX_BASE64 = 7_000_000;

    public static final List<String> OBJETIVOS = List.of(
            "COMPILAR_CON_ERROR", "COMPILAR_EXITOSO", "EJECUTAR_CON_ERROR", "EJECUTAR_EXITOSO");
    public static final List<String> MIME_TYPES = List.of("image/png", "image/jpeg", "image/gif", "image/webp");

    private static final String NOMBRE = "nombre";
    private static final String CONTENIDO_MARKDOWN = "contenidoMarkdown";
    private static final String OBJETIVO = "objetivo";
    private static final String IMAGENES = "imagenes";
    private static final String MIME_TYPE = "mimeType";
    private static final String CONTENIDO_BASE64 = "contenidoBase64";

    private static final Set<String> CAMPOS_LECCION = Set.of(NOMBRE, CONTENIDO_MARKDOWN, OBJETIVO, IMAGENES);
    private static final Set<String> CAMPOS_IMAGEN = Set.of(NOMBRE, MIME_TYPE, CONTENIDO_BASE64);

    private LeccionRequestParser() {
    }

    /** POST: nombre, contenidoMarkdown y objetivo obligatorios; {@code imagenes} es opcional (por defecto, ninguna). */
    public static LeccionCreate parseCreate(Map<String, Object> cuerpo) {
        Datos d = leer(cuerpo, false);
        return new LeccionCreate(d.nombre(), d.contenidoMarkdown(), d.objetivo(), d.imagenes());
    }

    /** PUT: reemplazo completo; {@code imagenes} es obligatorio (una lista vacía elimina todas las imágenes). */
    public static LeccionUpdate parseUpdate(Map<String, Object> cuerpo) {
        Datos d = leer(cuerpo, true);
        return new LeccionUpdate(d.nombre(), d.contenidoMarkdown(), d.objetivo(), d.imagenes());
    }

    /**
     * Clave con la que se comparan dos nombres para saber si son "el mismo": sin mayúsculas, sin acentos y sin
     * espacios finales. Aproxima la comparación de MariaDB ({@code utf8mb4_general_ci}) para avisar con un error de
     * validación claro antes de llegar a la restricción única de la BD.
     */
    public static String claveNombre(String nombre) {
        String sinAcentos = Normalizer.normalize(nombre, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return sinAcentos.toLowerCase(Locale.ROOT).replaceAll(" +$", "");
    }

    // ------------------------------------------------------------------

    private record Datos(String nombre, String contenidoMarkdown, String objetivo, List<ImagenEntrada> imagenes) {
    }

    private static Datos leer(Map<String, Object> cuerpo, boolean imagenesObligatorias) {
        if (cuerpo == null) {
            throw ApiException.cuerpoInvalido();
        }
        rechazarCamposDesconocidos(cuerpo);
        List<ErrorCampo> errores = new ArrayList<>();
        String nombre = leerNombre(cuerpo, errores);
        String markdown = leerMarkdown(cuerpo, errores);
        String objetivo = leerObjetivo(cuerpo, errores);
        List<ImagenEntrada> imagenes = leerImagenes(cuerpo, imagenesObligatorias, errores);
        if (!errores.isEmpty()) {
            throw ApiException.validacion(errores);
        }
        return new Datos(nombre, markdown, objetivo, imagenes);
    }

    /** Propiedad desconocida (en la lección o dentro de una imagen): el contrato declara additionalProperties:false. */
    private static void rechazarCamposDesconocidos(Map<String, Object> cuerpo) {
        if (!CAMPOS_LECCION.containsAll(cuerpo.keySet())) {
            throw ApiException.cuerpoInvalido();
        }
        if (cuerpo.get(IMAGENES) instanceof List<?> lista) {
            for (Object elemento : lista) {
                if (elemento instanceof Map<?, ?> imagen && !CAMPOS_IMAGEN.containsAll(imagen.keySet())) {
                    throw ApiException.cuerpoInvalido();
                }
            }
        }
    }

    private static String leerNombre(Map<String, Object> cuerpo, List<ErrorCampo> errores) {
        Object valor = cuerpo.get(NOMBRE);
        if (valor == null) {
            errores.add(new ErrorCampo(NOMBRE, "es obligatorio"));
            return null;
        }
        if (!(valor instanceof String texto)) {
            errores.add(new ErrorCampo(NOMBRE, "debe ser texto"));
            return null;
        }
        if (texto.isBlank()) {
            errores.add(new ErrorCampo(NOMBRE, "no puede estar en blanco"));
            return null;
        }
        if (texto.codePointCount(0, texto.length()) > MAX_NOMBRE) {
            errores.add(new ErrorCampo(NOMBRE, "debe tener como máximo " + MAX_NOMBRE + " caracteres"));
            return null;
        }
        return texto;
    }

    private static String leerMarkdown(Map<String, Object> cuerpo, List<ErrorCampo> errores) {
        Object valor = cuerpo.get(CONTENIDO_MARKDOWN);
        if (valor == null) {
            errores.add(new ErrorCampo(CONTENIDO_MARKDOWN, "es obligatorio"));
            return null;
        }
        if (!(valor instanceof String texto)) {
            errores.add(new ErrorCampo(CONTENIDO_MARKDOWN, "debe ser texto"));
            return null;
        }
        if (texto.codePointCount(0, texto.length()) > MAX_MARKDOWN) {
            errores.add(new ErrorCampo(CONTENIDO_MARKDOWN, "debe tener como máximo " + MAX_MARKDOWN + " caracteres"));
            return null;
        }
        return texto; // vacío es válido: el contrato no exige longitud mínima
    }

    private static String leerObjetivo(Map<String, Object> cuerpo, List<ErrorCampo> errores) {
        Object valor = cuerpo.get(OBJETIVO);
        if (valor == null) {
            errores.add(new ErrorCampo(OBJETIVO, "es obligatorio"));
            return null;
        }
        if (!(valor instanceof String texto) || !OBJETIVOS.contains(texto)) {
            errores.add(new ErrorCampo(OBJETIVO, "debe ser uno de: " + String.join(", ", OBJETIVOS)));
            return null;
        }
        return texto;
    }

    private static List<ImagenEntrada> leerImagenes(Map<String, Object> cuerpo, boolean obligatorias,
                                                    List<ErrorCampo> errores) {
        Object valor = cuerpo.get(IMAGENES);
        if (valor == null) {
            if (obligatorias) {
                errores.add(new ErrorCampo(IMAGENES, "es obligatorio (envíe [] para no tener imágenes)"));
                return null;
            }
            if (cuerpo.containsKey(IMAGENES)) {
                errores.add(new ErrorCampo(IMAGENES, "no puede ser null (omita el campo o envíe [])"));
                return null;
            }
            return List.of(); // ausente en un POST: la lección no lleva imágenes
        }
        if (!(valor instanceof List<?> lista)) {
            errores.add(new ErrorCampo(IMAGENES, "debe ser una lista"));
            return null;
        }
        if (lista.size() > MAX_IMAGENES) {
            errores.add(new ErrorCampo(IMAGENES, "debe tener como máximo " + MAX_IMAGENES + " imágenes"));
            return null;
        }
        List<ImagenEntrada> resultado = new ArrayList<>();
        Set<String> clavesVistas = new HashSet<>();
        for (int i = 0; i < lista.size(); i++) {
            ImagenEntrada imagen = leerImagen(lista.get(i), i, clavesVistas, errores);
            if (imagen != null) {
                resultado.add(imagen);
            }
        }
        return resultado;
    }

    private static ImagenEntrada leerImagen(Object elemento, int indice, Set<String> clavesVistas,
                                            List<ErrorCampo> errores) {
        String prefijo = IMAGENES + "[" + indice + "]";
        if (!(elemento instanceof Map<?, ?> mapa)) {
            errores.add(new ErrorCampo(prefijo, "debe ser un objeto"));
            return null;
        }
        int erroresAntes = errores.size();

        String nombre = null;
        Object nombreCrudo = mapa.get(NOMBRE);
        if (nombreCrudo == null) {
            errores.add(new ErrorCampo(prefijo + "." + NOMBRE, "es obligatorio"));
        } else if (!(nombreCrudo instanceof String texto)) {
            errores.add(new ErrorCampo(prefijo + "." + NOMBRE, "debe ser texto"));
        } else if (texto.isEmpty() || texto.codePointCount(0, texto.length()) > MAX_NOMBRE) {
            errores.add(new ErrorCampo(prefijo + "." + NOMBRE, "debe tener entre 1 y " + MAX_NOMBRE + " caracteres"));
        } else if (texto.indexOf('/') >= 0 || texto.indexOf('\\') >= 0) {
            errores.add(new ErrorCampo(prefijo + "." + NOMBRE, "no puede contener '/' ni '\\'"));
        } else if (!clavesVistas.add(claveNombre(texto))) {
            errores.add(new ErrorCampo(prefijo + "." + NOMBRE, "ya aparece en otra imagen de la lección"));
        } else {
            nombre = texto;
        }

        String mimeType = null;
        Object mimeCrudo = mapa.get(MIME_TYPE);
        if (mimeCrudo == null) {
            errores.add(new ErrorCampo(prefijo + "." + MIME_TYPE, "es obligatorio"));
        } else if (!(mimeCrudo instanceof String texto) || !MIME_TYPES.contains(texto)) {
            errores.add(new ErrorCampo(prefijo + "." + MIME_TYPE, "debe ser uno de: " + String.join(", ", MIME_TYPES)));
        } else {
            mimeType = texto;
        }

        byte[] contenido = null;
        Object base64Crudo = mapa.get(CONTENIDO_BASE64);
        if (base64Crudo == null) {
            errores.add(new ErrorCampo(prefijo + "." + CONTENIDO_BASE64, "es obligatorio"));
        } else if (!(base64Crudo instanceof String texto)) {
            errores.add(new ErrorCampo(prefijo + "." + CONTENIDO_BASE64, "debe ser texto en base64"));
        } else if (texto.length() > MAX_BASE64) {
            errores.add(new ErrorCampo(prefijo + "." + CONTENIDO_BASE64,
                    "debe tener como máximo " + MAX_BASE64 + " caracteres (≈ 5 MB)"));
        } else {
            contenido = decodificar(texto);
            if (contenido == null) {
                errores.add(new ErrorCampo(prefijo + "." + CONTENIDO_BASE64, "debe ser base64 válido"));
            } else if (contenido.length == 0) {
                errores.add(new ErrorCampo(prefijo + "." + CONTENIDO_BASE64, "no puede estar vacío"));
                contenido = null;
            }
        }

        boolean valida = errores.size() == erroresAntes && nombre != null && mimeType != null && contenido != null;
        return valida ? new ImagenEntrada(nombre, mimeType, contenido) : null;
    }

    /** Decodifica base64 estándar; devuelve null (en vez de lanzar) si el texto no es base64 válido. */
    private static byte[] decodificar(String base64) {
        try {
            return Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
