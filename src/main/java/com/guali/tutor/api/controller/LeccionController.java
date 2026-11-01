package com.guali.tutor.api.controller;

import com.guali.tutor.api.dto.LeccionRequestParser;
import com.guali.tutor.api.dto.LeccionResponse;
import com.guali.tutor.api.dto.LeccionResumen;
import com.guali.tutor.api.service.LeccionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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
 * Endpoints de /api/lecciones (las 5 operaciones del contrato aprobado). Las imágenes no tienen endpoints propios:
 * viajan dentro de la lección. Solo traduce HTTP: valida el cuerpo con el parser, delega en el servicio y arma el
 * código de estado y la cabecera Location. Los errores los traduce {@code GlobalExceptionHandler}.
 */
@RestController
@RequestMapping("/api/lecciones")
public class LeccionController {

    private final LeccionService service;

    public LeccionController(LeccionService service) {
        this.service = service;
    }

    @GetMapping
    public List<LeccionResumen> listar() {
        return service.listar();
    }

    @GetMapping("/{id}")
    public LeccionResponse obtener(@PathVariable("id") int id) {
        return service.obtener(id);
    }

    @PostMapping
    public ResponseEntity<LeccionResponse> crear(@RequestBody Map<String, Object> cuerpo) {
        LeccionResponse creada = service.crear(LeccionRequestParser.parseCreate(cuerpo));
        URI ubicacion = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(creada.id()).toUri();
        return ResponseEntity.created(ubicacion).body(creada);
    }

    @PutMapping("/{id}")
    public LeccionResponse reemplazar(@PathVariable("id") int id, @RequestBody Map<String, Object> cuerpo) {
        return service.reemplazar(id, LeccionRequestParser.parseUpdate(cuerpo));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable("id") int id) {
        service.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
