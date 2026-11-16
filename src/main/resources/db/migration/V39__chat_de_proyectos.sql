-- Chat grupal de cada proyecto (V39): mensajes del equipo con adjuntos, como
-- el chat de los tickets. Los archivos van a la zona privada de adjuntos
-- (adjuntos/proyectos) y solo se bajan por la API, con permiso del proyecto.
CREATE TABLE IF NOT EXISTS proyecto_mensaje (
    id_proyecto_mensaje         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    proyecto_proyecto_mensaje   UUID NOT NULL REFERENCES proyecto (id_proyecto),
    autor_proyecto_mensaje      UUID NOT NULL REFERENCES users (id_user),
    texto_proyecto_mensaje      TEXT,
    deleted_proyecto_mensaje    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at_proyecto_mensaje TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_proyecto_mensaje_fecha
    ON proyecto_mensaje (proyecto_proyecto_mensaje, created_at_proyecto_mensaje DESC);

CREATE TABLE IF NOT EXISTS proyecto_adjunto (
    id_proyecto_adjunto         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    mensaje_proyecto_adjunto    UUID NOT NULL REFERENCES proyecto_mensaje (id_proyecto_mensaje),
    nombre_proyecto_adjunto     VARCHAR(255) NOT NULL,
    ruta_proyecto_adjunto       VARCHAR(400) NOT NULL,
    mime_proyecto_adjunto       VARCHAR(120) NOT NULL,
    tamano_proyecto_adjunto     BIGINT NOT NULL,
    created_at_proyecto_adjunto TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_proyecto_adjunto_mensaje ON proyecto_adjunto (mensaje_proyecto_adjunto);

-- Hasta cuándo leyó cada persona el chat de un proyecto (para los "no leídos").
CREATE TABLE IF NOT EXISTS proyecto_lectura (
    proyecto_proyecto_lectura UUID NOT NULL REFERENCES proyecto (id_proyecto),
    usuario_proyecto_lectura  UUID NOT NULL REFERENCES users (id_user),
    leido_at_proyecto_lectura TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (proyecto_proyecto_lectura, usuario_proyecto_lectura)
);
