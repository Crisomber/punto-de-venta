CREATE TABLE usuario (
    id             UUID PRIMARY KEY,
    nombre         VARCHAR(120) NOT NULL,
    rol            VARCHAR(20)  NOT NULL CHECK (rol IN ('DUENO','MESERO','CAJERO','CHEF')),
    estacion       VARCHAR(10)  CHECK (estacion IN ('P1','P2')),
    username       VARCHAR(60)  UNIQUE,
    password_hash  VARCHAR(255),
    pin_hash       VARCHAR(255),
    activo         BOOLEAN NOT NULL DEFAULT TRUE
);
