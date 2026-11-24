CREATE TABLE orden_item (
    id              UUID PRIMARY KEY,
    orden_id        UUID NOT NULL REFERENCES orden(id),
    platillo_id     UUID NOT NULL REFERENCES platillo(id),
    cantidad        INT NOT NULL,
    precio_unitario NUMERIC(10,2) NOT NULL,
    estado          VARCHAR(20) NOT NULL CHECK (estado IN ('PENDIENTE','EN_PREPARACION','LISTO'))
);
