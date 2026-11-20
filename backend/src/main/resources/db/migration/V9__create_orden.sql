CREATE TABLE orden (
    id              UUID PRIMARY KEY,
    mesa_id         UUID REFERENCES mesa(id),
    mesero_id       UUID NOT NULL REFERENCES usuario(id),
    tipo            VARCHAR(20) NOT NULL CHECK (tipo IN ('CONSUMO_LOCAL','PARA_LLEVAR')),
    estado          VARCHAR(20) NOT NULL CHECK (estado IN ('ABIERTA','ENVIADA','CERRADA','PAGADA')),
    fecha_apertura  TIMESTAMPTZ NOT NULL,
    fecha_cierre    TIMESTAMPTZ
);
