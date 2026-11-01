package com.guali.tutor.api.service;

import com.guali.tutor.api.dao.Configuracion;
import com.guali.tutor.api.dto.ConfiguracionCreate;
import com.guali.tutor.api.dto.ConfiguracionPatch;
import com.guali.tutor.api.dto.ConfiguracionResponse;
import com.guali.tutor.api.dto.ConfiguracionUpdate;
import com.guali.tutor.api.exception.ApiException;
import com.guali.tutor.api.repository.ConfiguracionRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/** Reglas de negocio de /api/configuracion. Las excepciones se traducen a errores del contrato en el manejador global. */
@Service
public class ConfiguracionService {

    /** Resultado de un PUT: la configuración resultante y si hubo que crearla (201) o solo reemplazarla (200). */
    public record ResultadoReemplazo(ConfiguracionResponse configuracion, boolean creada) {
    }

    private final ConfiguracionRepository repository;

    public ConfiguracionService(ConfiguracionRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<ConfiguracionResponse> listar() {
        return repository.findAll(Sort.by("id")).stream().map(ConfiguracionService::aRespuesta).toList();
    }

    @Transactional(readOnly = true)
    public ConfiguracionResponse obtener(int id) {
        validarId(id);
        return aRespuesta(buscar(id));
    }

    /** POST: crea con el id indicado; si ya existe, 409 CONFIGURACION_DUPLICADA (nunca sobrescribe). */
    @Transactional
    public ConfiguracionResponse crear(ConfiguracionCreate datos) {
        if (repository.existsById(datos.id())) {
            throw ApiException.conflicto("CONFIGURACION_DUPLICADA",
                    "Ya existe una configuración con id " + datos.id() + ".");
        }
        return aRespuesta(repository.save(new Configuracion(datos.id(), datos.jdkPath(), datos.leccionesRootPath())));
    }

    /** PUT: reemplazo completo; si no existe, se crea (el id lo elige el cliente en esta tabla). */
    @Transactional
    public ResultadoReemplazo reemplazar(int id, ConfiguracionUpdate datos) {
        validarId(id);
        Optional<Configuracion> existente = repository.findById(id);
        boolean creada = existente.isEmpty();
        Configuracion configuracion = existente.orElseGet(() -> new Configuracion(id, null, null));
        configuracion.setJdkPath(datos.jdkPath());
        configuracion.setLeccionesRootPath(datos.leccionesRootPath());
        return new ResultadoReemplazo(aRespuesta(repository.save(configuracion)), creada);
    }

    /** PATCH: solo cambia los campos presentes en el cuerpo; el resto se conserva. 404 si no existe. */
    @Transactional
    public ConfiguracionResponse actualizarParcialmente(int id, ConfiguracionPatch cambios) {
        validarId(id);
        Configuracion configuracion = buscar(id);
        if (cambios.jdkPath() != null) {
            configuracion.setJdkPath(cambios.jdkPath().valor());
        }
        if (cambios.leccionesRootPath() != null) {
            configuracion.setLeccionesRootPath(cambios.leccionesRootPath().valor());
        }
        return aRespuesta(repository.save(configuracion));
    }

    @Transactional
    public void eliminar(int id) {
        validarId(id);
        repository.delete(buscar(id));
    }

    // ------------------------------------------------------------------

    private Configuracion buscar(int id) {
        return repository.findById(id).orElseThrow(
                () -> ApiException.noEncontrado("No existe una configuración con id " + id + "."));
    }

    /** El contrato define los ids como enteros ≥ 1; 0 o negativos son un parámetro inválido, no un 404. */
    private static void validarId(int id) {
        if (id < 1) {
            throw ApiException.parametroInvalido("El identificador '" + id + "' no es un entero válido (debe ser ≥ 1).");
        }
    }

    private static ConfiguracionResponse aRespuesta(Configuracion c) {
        return new ConfiguracionResponse(c.getId(), c.getJdkPath(), c.getLeccionesRootPath());
    }
}
