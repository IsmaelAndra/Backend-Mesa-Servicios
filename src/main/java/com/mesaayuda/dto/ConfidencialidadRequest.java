package com.mesaayuda.dto;

import java.util.List;
import java.util.UUID;

/** Marcar o desmarcar un ticket como confidencial y decidir quién más lo ve. */
public record ConfidencialidadRequest(boolean confidencial, boolean todaElArea, List<UUID> personas) {
}
