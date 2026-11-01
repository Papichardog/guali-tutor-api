package com.guali.tutor.api.service;

import com.guali.tutor.api.dao.Leccion;
import com.guali.tutor.api.dao.LeccionImagen;
import com.guali.tutor.api.dto.FechaHora;
import com.guali.tutor.api.dto.ImagenEntrada;
import com.guali.tutor.api.dto.ImagenResponse;
import com.guali.tutor.api.dto.LeccionCreate;
import com.guali.tutor.api.dto.LeccionResponse;
import com.guali.tutor.api.dto.LeccionResumen;
import com.guali.tutor.api.dto.LeccionUpdate;
import com.guali.tutor.api.exception.ApiException;
import com.guali.tutor.api.repository.LeccionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Base64;
import java.util.List;

/**
 * Reglas de negocio de /api/lecciones. Las imágenes forman parte de la lección: se crean, reemplazan y borran con
 * ella (cascada y orphanRemoval de la entidad). Los errores se traducen al formato del contrato en el manejador global.
 */
@Service
public class LeccionService {

    private final LeccionRepository repository;

    public LeccionService(LeccionRepository repository) {
        this.repository = repository;
    }

    /** Listado liviano, por nombre: no lee el Markdown ni las imágenes. */
    @Transactional(readOnly = true)
    public List<LeccionResumen> listar() {
        return repository.listarResumen();
    }

    @Transactional(readOnly = true)
    public LeccionResponse obtener(int id) {
        validarId(id);
        return aRespuesta(buscar(id));
    }

    /** POST: crea la lección con sus imágenes; si el nombre ya existe, 409 LECCION_DUPLICADA y no se modifica nada. */
    @Transactional
    public LeccionResponse crear(LeccionCreate datos) {
        if (repository.existsByNombreIgnoreCase(datos.nombre())) {
            throw duplicada(datos.nombre());
        }
        Leccion leccion = new Leccion(datos.nombre(), datos.contenidoMarkdown(), datos.objetivo());
        agregarImagenes(leccion, datos.imagenes());
        return aRespuesta(repository.save(leccion));
    }

    /**
     * PUT: reemplazo completo. Dejar el nombre propio no es conflicto; usar el de OTRA lección, sí (409).
     * {@code fechaImportacion} no cambia. Las imágenes enviadas sustituyen a las anteriores.
     */
    @Transactional
    public LeccionResponse reemplazar(int id, LeccionUpdate datos) {
        validarId(id);
        Leccion leccion = buscar(id);
        if (repository.existsByNombreIgnoreCaseAndIdNot(datos.nombre(), id)) {
            throw duplicada(datos.nombre());
        }
        leccion.setNombre(datos.nombre());
        leccion.setContenidoMarkdown(datos.contenidoMarkdown());
        leccion.setObjetivo(datos.objetivo());

        // Se quitan las imágenes viejas (orphanRemoval las borra de la BD) y se ejecutan esos DELETE YA, antes de
        // añadir las nuevas. Es una medida defensiva: así el resultado no depende del orden interno en que Hibernate
        // ejecute DELETE e INSERT al confirmar. Si el INSERT de una imagen nueva se ejecutara antes que el DELETE de
        // una vieja con el mismo nombre, chocaría con el índice único (leccion_id, nombre).
        leccion.getImagenes().clear();
        repository.flush();
        agregarImagenes(leccion, datos.imagenes());
        return aRespuesta(repository.save(leccion));
    }

    /**
     * DELETE: borra la lección y, en cascada, sus imágenes. No toca el historial: la clave foránea de la BD deja
     * sus registros con {@code leccion_id = NULL} (ON DELETE SET NULL) y conservan nombre y estado.
     */
    @Transactional
    public void eliminar(int id) {
        validarId(id);
        repository.delete(buscar(id));
    }

    // ------------------------------------------------------------------

    private Leccion buscar(int id) {
        return repository.findById(id).orElseThrow(
                () -> ApiException.noEncontrado("No existe una lección con id " + id + "."));
    }

    private static void agregarImagenes(Leccion leccion, List<ImagenEntrada> imagenes) {
        for (ImagenEntrada imagen : imagenes) {
            leccion.agregarImagen(new LeccionImagen(imagen.nombre(), imagen.mimeType(), imagen.contenido()));
        }
    }

    private static ApiException duplicada(String nombre) {
        return ApiException.conflicto("LECCION_DUPLICADA", "Ya existe una lección con el nombre '" + nombre + "'.");
    }

    /** El contrato define los ids como enteros ≥ 1; 0 o negativos son un parámetro inválido, no un 404. */
    private static void validarId(int id) {
        if (id < 1) {
            throw ApiException.parametroInvalido("El identificador '" + id + "' no es un entero válido (debe ser ≥ 1).");
        }
    }

    /** Debe llamarse dentro de la transacción: aquí se carga (perezosamente) la colección de imágenes. */
    private static LeccionResponse aRespuesta(Leccion leccion) {
        List<ImagenResponse> imagenes = leccion.getImagenes().stream()
                .map(i -> new ImagenResponse(i.getNombre(), i.getMimeType(),
                        Base64.getEncoder().encodeToString(i.getContenido())))
                .toList();
        return new LeccionResponse(leccion.getId(), leccion.getNombre(), leccion.getContenidoMarkdown(),
                leccion.getObjetivo(), FechaHora.formatear(leccion.getFechaImportacion()), imagenes);
    }
}
