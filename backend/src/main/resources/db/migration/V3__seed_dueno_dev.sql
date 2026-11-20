-- Dev-only seed: the dueno-creates-staff provisioning flow doesn't exist yet,
-- so this is how the first login happens in a freshly migrated database.
-- username: admin / password: cambiame123
INSERT INTO usuario (id, nombre, rol, username, password_hash, activo)
VALUES (gen_random_uuid(), 'Administrador', 'DUENO', 'admin',
        '$2a$10$SF7W3tjD8773gIHmBoXy9e0NHQKce9LwTIuoYJHWs8oo5WwcPSIji', TRUE);
