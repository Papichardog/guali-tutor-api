package com.guali.tutor.api.dto;

import com.guali.tutor.api.exception.ApiException;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Convierte y valida los cuerpos JSON de /api/configuracion según el contrato OpenAPI.
 *
 * <p>Los cuerpos llegan como {@code Map} (y no como un DTO que Jackson rellena) porque el contrato
 * depende de distinguir un campo <b>ausente</b> de uno con <b>{@code null}</b>: PUT exige que ambos
 * campos estén presentes (aunque sea con null), y PATCH (merge patch) deja intacto lo ausente y borra
 * lo que viene en null. Con un DTO ambos casos serían indistinguibles. Esta clase no depende de Spring,
 * así que su lógica se prueba aislada.
 */
public final class ConfiguracionRequestParser {

    private static final String ID = "id";
    private static final String JDK_PATH = "jdkPath";
    private static final String LECCIONES_ROOT_PATH = "leccionesRootPath";
    private static final int MAX_RUTA = 500;

    private static final Set<String> CAMPOS_CREATE = Set.of(ID, JDK_PATH, LECCIONES_ROOT_PATH);
    private static final Set<String> CAMPOS_RUTAS = Set.of(JDK_PATH, LECCIONES_ROOT_PATH);

    private ConfiguracionRequestParser() {
    }

    /** POST: {@code id} obligatorio (entero ≥ 1); las dos rutas son opcionales y anulables. */
    public static ConfiguracionCreate parseCreate(Map<String, Object> cuerpo) {
        exigirCuerpo(cuerpo);
        rechazarCamposDesconocidos(cuerpo, CAMPOS_CREATE);
        List<ErrorCampo> errores = new ArrayList<>();
        Integer id = leerId(cuerpo, errores);
        String jdkPath = leerRuta(cuerpo, JDK_PATH, errores);
        String raiz = leerRuta(cuerpo, LECCIONES_ROOT_PATH, errores);
        lanzarSiHayErrores(errores);
        return new ConfiguracionCreate(id, jdkPath, raiz);
    }

    /** PUT: reemplazo completo; las dos rutas deben venir (con valor o con null). {@code id} no se acepta: va en la URL. */
    public static ConfiguracionUpdate parseUpdate(Map<String, Object> cuerpo) {
        exigirCuerpo(cuerpo);
        rechazarCamposDesconocidos(cuerpo, CAMPOS_RUTAS);
        List<ErrorCampo> errores = new ArrayList<>();
        for (String campo : List.of(JDK_PATH, LECCIONES_ROOT_PATH)) {
            if (!cuerpo.containsKey(campo)) {
                errores.add(new ErrorCampo(campo, "es obligatorio (envíe null si no hay valor)"));
            }
        }
        String jdkPath = leerRuta(cuerpo, JDK_PATH, errores);
        String raiz = leerRuta(cuerpo, LECCIONES_ROOT_PATH, errores);
        lanzarSiHayErrores(errores);
        return new ConfiguracionUpdate(jdkPath, raiz);
    }

    /** PATCH (merge patch): al menos un campo; lo ausente no cambia, null borra, un texto actualiza. */
    public static ConfiguracionPatch parsePatch(Map<String, Object> cuerpo) {
        exigirCuerpo(cuerpo);
        rechazarCamposDesconocidos(cuerpo, CAMPOS_RUTAS);
        if (cuerpo.isEmpty()) {
            throw ApiException.validacion(List.of(new ErrorCampo("(cuerpo)", "debe incluir al menos un campo")));
        }
        List<ErrorCampo> errores = new ArrayList<>();
        ValorPatch<String> jdkPath = cuerpo.containsKey(JDK_PATH)
                ? new ValorPatch<>(leerRuta(cuerpo, JDK_PATH, errores)) : null;
        ValorPatch<String> raiz = cuerpo.containsKey(LECCIONES_ROOT_PATH)
                ? new ValorPatch<>(leerRuta(cuerpo, LECCIONES_ROOT_PATH, errores)) : null;
        lanzarSiHayErrores(errores);
        return new ConfiguracionPatch(jdkPath, raiz);
    }

    // ------------------------------------------------------------------

    private static void exigirCuerpo(Map<String, Object> cuerpo) {
        if (cuerpo == null) {
            throw ApiException.cuerpoInvalido();
        }
    }

    /** Propiedad desconocida: el contrato declara additionalProperties:false. */
    private static void rechazarCamposDesconocidos(Map<String, Object> cuerpo, Set<String> permitidos) {
        for (String clave : cuerpo.keySet()) {
            if (!permitidos.contains(clave)) {
                throw ApiException.cuerpoInvalido();
            }
        }
    }

    private static Integer leerId(Map<String, Object> cuerpo, List<ErrorCampo> errores) {
        Object valor = cuerpo.get(ID);
        if (valor == null) {
            errores.add(new ErrorCampo(ID, "es obligatorio"));
            return null;
        }
        if (valor instanceof Integer entero) {
            if (entero < 1) {
                errores.add(new ErrorCampo(ID, "debe ser mayor o igual a 1"));
                return null;
            }
            return entero;
        }
        if (valor instanceof Long largo) {
            errores.add(new ErrorCampo(ID, largo < 1
                    ? "debe ser mayor o igual a 1" : "debe ser menor o igual a " + Integer.MAX_VALUE));
            return null;
        }
        if (valor instanceof BigInteger) {
            errores.add(new ErrorCampo(ID, "debe ser menor o igual a " + Integer.MAX_VALUE));
            return null;
        }
        errores.add(new ErrorCampo(ID, "debe ser un número entero"));
        return null;
    }

    /** Lee una ruta: ausente o null -> null; texto de hasta 500 caracteres -> el texto; otra cosa -> error. */
    private static String leerRuta(Map<String, Object> cuerpo, String campo, List<ErrorCampo> errores) {
        Object valor = cuerpo.get(campo);
        if (valor == null) {
            return null;
        }
        if (!(valor instanceof String texto)) {
            errores.add(new ErrorCampo(campo, "debe ser texto o null"));
            return null;
        }
        if (texto.codePointCount(0, texto.length()) > MAX_RUTA) {
            errores.add(new ErrorCampo(campo, "debe tener como máximo " + MAX_RUTA + " caracteres"));
            return null;
        }
        return texto;
    }

    private static void lanzarSiHayErrores(List<ErrorCampo> errores) {
        if (!errores.isEmpty()) {
            throw ApiException.validacion(errores);
        }
    }
}
