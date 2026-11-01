package com.guali.tutor.api.service;

import com.guali.tutor.api.dao.EstadoHistorial;
import com.guali.tutor.api.dao.Historial;
import com.guali.tutor.api.dao.Leccion;
import com.guali.tutor.api.dto.ErrorCampo;
import com.guali.tutor.api.dto.FechaHora;
import com.guali.tutor.api.dto.HistorialCreate;
import com.guali.tutor.api.dto.HistorialPatch;
import com.guali.tutor.api.dto.HistorialResponse;
import com.guali.tutor.api.dto.HistorialUpdate;
import com.guali.tutor.api.exception.ApiException;
import com.guali.tutor.api.repository.HistorialRepository;
import com.guali.tutor.api.repository.LeccionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Reglas de negocio de /api/historial. La regla central: una vez {@code Completada}, un registro NUNCA vuelve a
 * {@code Pendiente} (409 {@code ESTADO_NO_REGRESIVO}), ni por PUT ni por PATCH. Orden de las comprobaciones en una
 * actualización: el registro existe (404) → la lección referenciada existe (400) → no hay regresión de estado (409);
 * así un error de la solicitud siempre se informa antes que un conflicto con el estado guardado. Los errores se traducen
 * al formato del contrato en el manejador global.
 */
@Service
public class HistorialService {

    private final HistorialRepository repository;
    private final LeccionRepository leccionRepository;

    public HistorialService(HistorialRepository repository, LeccionRepository leccionRepository) {
        this.repository = repository;
        this.leccionRepository = leccionRepository;
    }

    /** Todo el historial, por hora de apertura y luego por id, ascendentes. */
    @Transactional(readOnly = true)
    public List<HistorialResponse> listar() {
        return repository.findAllByOrderByHoraAperturaAscIdAsc().stream().map(HistorialService::aRespuesta).toList();
    }

    @Transactional(readOnly = true)
    public HistorialResponse obtener(int id) {
        validarId(id);
        return aRespuesta(buscar(id));
    }

    /** POST: sin hora, usa la actual del servidor; sin estado, Pendiente; sin leccionId, lección local (null). */
    @Transactional
    public HistorialResponse crear(HistorialCreate datos) {
        Leccion leccion = resolverLeccion(datos.leccionId());
        LocalDateTime hora = datos.horaApertura() != null
                ? datos.horaApertura() : LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        EstadoHistorial estado = datos.estado() != null ? datos.estado() : EstadoHistorial.PENDIENTE;
        return aRespuesta(repository.save(new Historial(leccion, datos.nombreLeccion(), hora, estado)));
    }

    /** PUT: reemplazo completo de un registro existente (no crea). */
    @Transactional
    public HistorialResponse reemplazar(int id, HistorialUpdate datos) {
        validarId(id);
        Historial historial = buscar(id);
        Leccion leccion = resolverLeccion(datos.leccionId());
        impedirRegresion(historial, datos.estado());

        historial.setLeccion(leccion);
        historial.setNombreLeccion(datos.nombreLeccion());
        historial.setHoraApertura(datos.horaApertura());
        historial.setEstado(datos.estado());
        return aRespuesta(repository.save(historial));
    }

    /**
     * PATCH: solo cambia los campos presentes. Es la operación con la que el cliente marca un intento como Completada.
     * Es idempotente: enviar Completada a un registro que ya lo está responde 200 sin cambios.
     */
    @Transactional
    public HistorialResponse actualizarParcialmente(int id, HistorialPatch cambios) {
        validarId(id);
        Historial historial = buscar(id);
        // Primero se comprueba todo; solo después se modifica algo.
        Leccion leccion = cambios.leccionId() != null ? resolverLeccion(cambios.leccionId().valor()) : null;
        if (cambios.estado() != null) {
            impedirRegresion(historial, cambios.estado().valor());
        }

        if (cambios.leccionId() != null) {
            historial.setLeccion(leccion);
        }
        if (cambios.nombreLeccion() != null) {
            historial.setNombreLeccion(cambios.nombreLeccion().valor());
        }
        if (cambios.horaApertura() != null) {
            historial.setHoraApertura(cambios.horaApertura().valor());
        }
        if (cambios.estado() != null) {
            historial.setEstado(cambios.estado().valor());
        }
        return aRespuesta(repository.save(historial));
    }

    @Transactional
    public void eliminar(int id) {
        validarId(id);
        repository.delete(buscar(id));
    }

    // ------------------------------------------------------------------

    private Historial buscar(int id) {
        return repository.findById(id).orElseThrow(
                () -> ApiException.noEncontrado("No existe un registro de historial con id " + id + "."));
    }

    /**
     * Devuelve una referencia a la lección (sin leerla de la BD: solo hace falta su id para la clave foránea), o
     * {@code null} si no se indicó ninguna. Si el id no corresponde a una lección, 400 REFERENCIA_INVALIDA.
     */
    private Leccion resolverLeccion(Integer leccionId) {
        if (leccionId == null) {
            return null;
        }
        if (!leccionRepository.existsById(leccionId)) {
            throw ApiException.referenciaInvalida("No existe una lección con id " + leccionId + ".",
                    List.of(new ErrorCampo("leccionId", "no existe una lección con ese id")));
        }
        return leccionRepository.getReferenceById(leccionId);
    }

    /** La regla del profesor: lo Completada no vuelve a Pendiente. Mantener Completada sí es válido. */
    private static void impedirRegresion(Historial actual, EstadoHistorial nuevo) {
        if (actual.getEstado() == EstadoHistorial.COMPLETADA && nuevo == EstadoHistorial.PENDIENTE) {
            throw ApiException.conflicto("ESTADO_NO_REGRESIVO", "Un registro Completada no puede volver a Pendiente.");
        }
    }

    /** El contrato define los ids como enteros ≥ 1; 0 o negativos son un parámetro inválido, no un 404. */
    private static void validarId(int id) {
        if (id < 1) {
            throw ApiException.parametroInvalido("El identificador '" + id + "' no es un entero válido (debe ser ≥ 1).");
        }
    }

    /** Debe llamarse dentro de la transacción. Del id de la lección solo se lee su identificador (no inicializa la lección). */
    private static HistorialResponse aRespuesta(Historial historial) {
        Integer leccionId = historial.getLeccion() != null ? historial.getLeccion().getId() : null;
        return new HistorialResponse(historial.getId(), leccionId, historial.getNombreLeccion(),
                FechaHora.formatear(historial.getHoraApertura()), historial.getEstado().getTexto());
    }
}
