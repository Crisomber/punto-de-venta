CREATE TABLE ticket (
    id              UUID PRIMARY KEY,
    orden_id        UUID NOT NULL REFERENCES orden(id),
    estacion        VARCHAR(10) NOT NULL CHECK (estacion IN ('P1','P2')),
    fecha_generado  TIMESTAMPTZ NOT NULL,
    estado          VARCHAR(10) NOT NULL CHECK (estado IN ('PENDIENTE','IMPRESO'))
);

ALTER TABLE orden_item
    ADD COLUMN ticket_id UUID REFERENCES ticket(id);
