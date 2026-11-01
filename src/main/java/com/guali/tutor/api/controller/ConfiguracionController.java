package com.guali.tutor.api.controller;

import com.guali.tutor.api.dto.ConfiguracionRequestParser;
import com.guali.tutor.api.dto.ConfiguracionResponse;
import com.guali.tutor.api.service.ConfiguracionService;
import com.guali.tutor.api.service.ConfiguracionService.ResultadoReemplazo;
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
 * Endpoints de /api/configuracion (6 operaciones del contrato aprobado). Solo traduce HTTP: valida el
 * cuerpo con el parser, delega en el servicio y arma el código de estado y la cabecera Location.
 * Los errores no se manejan aquí: se lanzan y los traduce {@code GlobalExceptionHandler}.
 */
@RestController
@RequestMapping("/api/configuracion")
public class ConfiguracionController {

    private final ConfiguracionService service;

    public ConfiguracionController(ConfiguracionService service) {
        this.service = service;
    }

    @GetMapping
    public List<ConfiguracionResponse> listar() {
        return service.listar();
    }

    @GetMapping("/{id}")
    public ConfiguracionResponse obtener(@PathVariable("id") int id) {
        return service.obtener(id);
    }

    @PostMapping
    public ResponseEntity<ConfiguracionResponse> crear(@RequestBody Map<String, Object> cuerpo) {
        ConfiguracionResponse creada = service.crear(ConfiguracionRequestParser.parseCreate(cuerpo));
        URI ubicacion = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(creada.id()).toUri();
        return ResponseEntity.created(ubicacion).body(creada);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ConfiguracionResponse> reemplazar(@PathVariable("id") int id,
                                                            @RequestBody Map<String, Object> cuerpo) {
        ResultadoReemplazo resultado = service.reemplazar(id, ConfiguracionRequestParser.parseUpdate(cuerpo));
        if (resultado.creada()) {
            URI ubicacion = ServletUriComponentsBuilder.fromCurrentRequest().build().toUri();
            return ResponseEntity.created(ubicacion).body(resultado.configuracion());
        }
        return ResponseEntity.ok(resultado.configuracion());
    }

    @PatchMapping("/{id}")
    public ConfiguracionResponse actualizarParcialmente(@PathVariable("id") int id,
                                                        @RequestBody Map<String, Object> cuerpo) {
        return service.actualizarParcialmente(id, ConfiguracionRequestParser.parsePatch(cuerpo));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable("id") int id) {
        service.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
