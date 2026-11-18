package com.mesaayuda.mantenimiento;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Público (lo pide la barra superior y el login): solo dice el ambiente. */
@RestController
public class AmbienteController {

    private final Ambiente ambiente;

    public AmbienteController(Ambiente ambiente) {
        this.ambiente = ambiente;
    }

    @GetMapping("/pages/public/ambiente")
    public Map<String, String> ambiente() {
        return Map.of("ambiente", ambiente.valor());
    }
}
