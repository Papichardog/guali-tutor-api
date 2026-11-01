package com.guali.tutor.api.controller;

import com.guali.tutor.api.dto.HistorialRequestParser;
import com.guali.tutor.api.dto.HistorialResponse;
import com.guali.tutor.api.service.HistorialService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * Endpoints de /api/historial (las 6 operaciones del contrato aprobado). Solo traduce HTTP: valida el cuerpo con el
 * parser, delega en el servicio y arma el código de estado y la cabecera Location. Los errores no se manejan aquí:
 * se lanzan y los traduce {@code GlobalExceptionHandler}.
 */
@RestController
@RequestMapping("/api/historial")
public class HistorialController {

    private final HistorialService service;

    public HistorialController(HistorialService service) {
        this.service = service;
    }

    @GetMapping
    public List<HistorialResponse> listar() {
        return service.listar();
    }

    @GetMapping("/{id}")
    public HistorialResponse obtener(@PathVariable("id") int id) {
        return service.obtener(id);
    }

    @PostMapping
    public ResponseEntity<HistorialResponse> crear(@RequestBody Map<String, Object> cuerpo) {
        HistorialResponse creado = service.crear(HistorialRequestParser.parseCreate(cuerpo));
        URI ubicacion = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(creado.id()).toUri();
        return ResponseEntity.created(ubicacion).body(creado);
    }

    @PutMapping("/{id}")
    public HistorialResponse reemplazar(@PathVariable("id") int id, @RequestBody Map<String, Object> cuerpo) {
        return service.reemplazar(id, HistorialRequestParser.parseUpdate(cuerpo));
    }

    @PatchMapping("/{id}")
    public HistorialResponse actualizarParcialmente(@PathVariable("id") int id,
                                                    @RequestBody Map<String, Object> cuerpo) {
        return service.actualizarParcialmente(id, HistorialRequestParser.parsePatch(cuerpo));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable("id") int id) {
        service.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
