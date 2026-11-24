CREATE TABLE platillo_modificador (
    platillo_id    UUID NOT NULL REFERENCES platillo(id),
    modificador_id UUID NOT NULL REFERENCES modificador(id),
    PRIMARY KEY (platillo_id, modificador_id)
);
