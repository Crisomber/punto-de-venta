CREATE TABLE pago (
    id          UUID PRIMARY KEY,
    orden_id    UUID NOT NULL UNIQUE REFERENCES orden(id),
    cajero_id   UUID NOT NULL REFERENCES usuario(id),
    metodo      VARCHAR(10) NOT NULL CHECK (metodo IN ('EFECTIVO','TARJETA')),
    monto       NUMERIC(10,2) NOT NULL,
    fecha       TIMESTAMPTZ NOT NULL
);
