CREATE TABLE platillo (
    id            UUID PRIMARY KEY,
    nombre        VARCHAR(120) NOT NULL,
    descripcion   VARCHAR(500),
    precio        NUMERIC(10,2) NOT NULL,
    categoria_id  UUID NOT NULL REFERENCES categoria(id),
    estacion      VARCHAR(10) NOT NULL CHECK (estacion IN ('P1','P2')),
    activo        BOOLEAN NOT NULL DEFAULT TRUE
);
