CREATE TABLE mesa (
    id      UUID PRIMARY KEY,
    numero  INT NOT NULL UNIQUE,
    estado  VARCHAR(10) NOT NULL CHECK (estado IN ('LIBRE','OCUPADA'))
);
