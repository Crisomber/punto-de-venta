CREATE TABLE orden_item_modificador (
    orden_item_id   UUID NOT NULL REFERENCES orden_item(id),
    modificador_id  UUID NOT NULL REFERENCES modificador(id),
    PRIMARY KEY (orden_item_id, modificador_id)
);
