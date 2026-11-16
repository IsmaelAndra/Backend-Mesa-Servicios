package com.mesaayuda.plantillas;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Plantillas de correo por área (V38). Va fuera de /admin/** porque el jefe de
 * área también entra; el servicio acota cada lectura y escritura a su área.
 */
@RestController
@RequestMapping("/plantillas-area")
public class PlantillaAreaController {

    private final PlantillaAreaService servicio;

    public PlantillaAreaController(PlantillaAreaService servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/mis-areas")
    @Operation(summary = "Áreas cuyas plantillas puede ver quien llama (vacío = todas)")
    public Set<UUID> misAreas() {
        Set<UUID> a = servicio.areasVisibles();
        return a == null ? Set.of() : a;
    }

    @GetMapping("/{areaId}")
    @Operation(summary = "Plantillas personalizables de un área, con la general al lado")
    public List<PlantillaAreaService.Vista> listar(@PathVariable UUID areaId) {
        return servicio.listar(areaId);
    }

    @PutMapping("/{areaId}/{nombre}")
    @Operation(summary = "Guardar la versión del área de una plantilla")
    public PlantillaAreaService.Vista guardar(@PathVariable UUID areaId, @PathVariable String nombre,
                                              @RequestBody PlantillaAreaService.Guardar r) {
        return servicio.guardar(areaId, nombre, r);
    }

    @DeleteMapping("/{areaId}/{nombre}")
    @Operation(summary = "Volver a la plantilla general")
    public ResponseEntity<Void> restablecer(@PathVariable UUID areaId, @PathVariable String nombre) {
        servicio.restablecer(areaId, nombre);
        return ResponseEntity.noContent().build();
    }
}
