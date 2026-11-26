ALTER TABLE platillo ALTER COLUMN precio DROP NOT NULL;

CREATE TABLE variante (
    id          UUID PRIMARY KEY,
    platillo_id UUID NOT NULL REFERENCES platillo(id),
    nombre      VARCHAR(60) NOT NULL,
    precio      NUMERIC(10,2) NOT NULL,
    orden_index INT NOT NULL
);

ALTER TABLE orden_item ADD COLUMN variante_nombre VARCHAR(60);
