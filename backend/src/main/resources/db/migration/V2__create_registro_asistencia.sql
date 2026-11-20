CREATE TABLE registro_asistencia (
    id          UUID PRIMARY KEY,
    usuario_id  UUID NOT NULL REFERENCES usuario(id),
    fecha_hora  TIMESTAMPTZ NOT NULL,
    tipo        VARCHAR(10) NOT NULL CHECK (tipo IN ('ENTRADA','SALIDA'))
);
