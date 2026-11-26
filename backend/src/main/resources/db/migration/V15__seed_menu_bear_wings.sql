-- Seed del menu real (Bear Wings! / Tacos El Bear Teques), transcrito por el dueno.
-- Todos los platillos quedan activos y en estacion P2 (cocina); el dueno puede
-- reasignar estacion o desactivar cualquier item despues desde su panel.

INSERT INTO categoria (id, nombre) VALUES (gen_random_uuid(), 'Alitas');
INSERT INTO categoria (id, nombre) VALUES (gen_random_uuid(), 'Boneless');
INSERT INTO categoria (id, nombre) VALUES (gen_random_uuid(), 'Hamburguesas');
INSERT INTO categoria (id, nombre) VALUES (gen_random_uuid(), 'Pizzas');
INSERT INTO categoria (id, nombre) VALUES (gen_random_uuid(), 'Tacos');
INSERT INTO categoria (id, nombre) VALUES (gen_random_uuid(), 'Platillos');
INSERT INTO categoria (id, nombre) VALUES (gen_random_uuid(), 'Alambres');
INSERT INTO categoria (id, nombre) VALUES (gen_random_uuid(), 'Tortas');
INSERT INTO categoria (id, nombre) VALUES (gen_random_uuid(), 'Pá compartir');
INSERT INTO categoria (id, nombre) VALUES (gen_random_uuid(), 'Gringas');
INSERT INTO categoria (id, nombre) VALUES (gen_random_uuid(), 'Hotdogs y más');
INSERT INTO categoria (id, nombre) VALUES (gen_random_uuid(), 'Combos');
INSERT INTO categoria (id, nombre) VALUES (gen_random_uuid(), 'Extras');

INSERT INTO modificador (id, nombre) VALUES (gen_random_uuid(), 'Salsa BBQ');
INSERT INTO modificador (id, nombre) VALUES (gen_random_uuid(), 'Salsa Mango-Habanero');
INSERT INTO modificador (id, nombre) VALUES (gen_random_uuid(), 'Salsa Búfalo');
INSERT INTO modificador (id, nombre) VALUES (gen_random_uuid(), 'Salsa Pimienta Limón');
INSERT INTO modificador (id, nombre) VALUES (gen_random_uuid(), 'Salsa BBQ-Chipotle');
INSERT INTO modificador (id, nombre) VALUES (gen_random_uuid(), 'Salsa Piña-Habanero');

INSERT INTO platillo (id, nombre, descripcion, precio, categoria_id, estacion, activo) VALUES (gen_random_uuid(), 'Alitas', 'Alitas de pollo bañadas en la salsa que elijas.', NULL, (SELECT id FROM categoria WHERE nombre = 'Alitas'), 'P2', TRUE);
INSERT INTO variante (id, platillo_id, nombre, precio, orden_index) VALUES (gen_random_uuid(), (SELECT id FROM platillo WHERE nombre = 'Alitas'), '6 piezas', 95.00, 0);
INSERT INTO variante (id, platillo_id, nombre, precio, orden_index) VALUES (gen_random_uuid(), (SELECT id FROM platillo WHERE nombre = 'Alitas'), '12 piezas', 159.00, 1);
INSERT INTO variante (id, platillo_id, nombre, precio, orden_index) VALUES (gen_random_uuid(), (SELECT id FROM platillo WHERE nombre = 'Alitas'), '18 piezas', 225.00, 2);

INSERT INTO platillo (id, nombre, descripcion, precio, categoria_id, estacion, activo) VALUES (gen_random_uuid(), 'Boneless', 'Crujientes trozos de pechuga de pollo capeados y bañados en la salsa que elijas.', 115.00, (SELECT id FROM categoria WHERE nombre = 'Boneless'), 'P2', TRUE);

INSERT INTO platillo_modificador (platillo_id, modificador_id) SELECT (SELECT id FROM platillo WHERE nombre = 'Alitas'), id FROM modificador WHERE nombre LIKE 'Salsa %';
INSERT INTO platillo_modificador (platillo_id, modificador_id) SELECT (SELECT id FROM platillo WHERE nombre = 'Boneless'), id FROM modificador WHERE nombre LIKE 'Salsa %';

INSERT INTO platillo (id, nombre, descripcion, precio, categoria_id, estacion, activo) VALUES (gen_random_uuid(), 'Hamburguesa Clásica', 'Incluye papas a la francesa.', 85.00, (SELECT id FROM categoria WHERE nombre = 'Hamburguesas'), 'P2', TRUE);
INSERT INTO platillo (id, nombre, descripcion, precio, categoria_id, estacion, activo) VALUES (gen_random_uuid(), 'Hamburguesa c/ Quesos', 'Incluye papas a la francesa.', 95.00, (SELECT id FROM categoria WHERE nombre = 'Hamburguesas'), 'P2', TRUE);
INSERT INTO platillo (id, nombre, descripcion, precio, categoria_id, estacion, activo) VALUES (gen_random_uuid(), 'Hamburguesa Hawaiana', 'Incluye papas a la francesa.', 95.00, (SELECT id FROM categoria WHERE nombre = 'Hamburguesas'), 'P2', TRUE);
INSERT INTO platillo (id, nombre, descripcion, precio, categoria_id, estacion, activo) VALUES (gen_random_uuid(), 'Hamburguesa de Pollo', 'Incluye papas a la francesa.', 95.00, (SELECT id FROM categoria WHERE nombre = 'Hamburguesas'), 'P2', TRUE);
INSERT INTO platillo (id, nombre, descripcion, precio, categoria_id, estacion, activo) VALUES (gen_random_uuid(), 'Hamburguesa BBQ', 'Incluye papas a la francesa.', 89.00, (SELECT id FROM categoria WHERE nombre = 'Hamburguesas'), 'P2', TRUE);
INSERT INTO platillo (id, nombre, descripcion, precio, categoria_id, estacion, activo) VALUES (gen_random_uuid(), 'Hamburguesa c/ 2 Carnes', 'Incluye papas a la francesa.', 115.00, (SELECT id FROM categoria WHERE nombre = 'Hamburguesas'), 'P2', TRUE);
INSERT INTO platillo (id, nombre, descripcion, precio, categoria_id, estacion, activo) VALUES (gen_random_uuid(), 'Hamburguesa Arrachera', 'Incluye papas a la francesa.', 115.00, (SELECT id FROM categoria WHERE nombre = 'Hamburguesas'), 'P2', TRUE);
INSERT INTO platillo (id, nombre, descripcion, precio, categoria_id, estacion, activo) VALUES (gen_random_uuid(), 'Hamburguesa Sirlon', 'Incluye papas a la francesa.', 115.00, (SELECT id FROM categoria WHERE nombre = 'Hamburguesas'), 'P2', TRUE);
INSERT INTO platillo (id, nombre, descripcion, precio, categoria_id, estacion, activo) VALUES (gen_random_uuid(), 'Hamburguesa al Pastor', 'Incluye papas a la francesa.', 110.00, (SELECT id FROM categoria WHERE nombre = 'Hamburguesas'), 'P2', TRUE);
INSERT INTO platillo (id, nombre, descripcion, precio, categoria_id, estacion, activo) VALUES (gen_random_uuid(), 'Hamburguesa c/ Aguacate', 'Incluye papas a la francesa.', 110.00, (SELECT id FROM categoria WHERE nombre = 'Hamburguesas'), 'P2', TRUE);

INSERT INTO platillo (id, nombre, descripcion, precio, categoria_id, estacion, activo) VALUES (gen_random_uuid(), 'Pizza Peperoni', NULL, NULL, (SELECT id FROM categoria WHERE nombre = 'Pizzas'), 'P2', TRUE);
INSERT INTO variante (id, platillo_id, nombre, precio, orden_index) VALUES (gen_random_uuid(), (SELECT id FROM platillo WHERE nombre = 'Pizza Peperoni'), 'Mediana', 175.00, 0);
INSERT INTO variante (id, platillo_id, nombre, precio, orden_index) VALUES (gen_random_uuid(), (SELECT id FROM platillo WHERE nombre = 'Pizza Peperoni'), 'Grande', 229.00, 1);
INSERT INTO variante (id, platillo_id, nombre, precio, orden_index) VALUES (gen_random_uuid(), (SELECT id FROM platillo WHERE nombre = 'Pizza Peperoni'), 'Familiar', 269.00, 2);
INSERT INTO platillo (id, nombre, descripcion, precio, categoria_id, estacion, activo) VALUES (gen_random_uuid(), 'Pizza Tres Quesos', NULL, NULL, (SELECT id FROM categoria WHERE nombre = 'Pizzas'), 'P2', TRUE);
INSERT INTO variante (id, platillo_id, nombre, precio, orden_index) VALUES (gen_random_uuid(), (SELECT id FROM platillo WHERE nombre = 'Pizza Tres Quesos'), 'Mediana', 175.00, 0);
INSERT INTO variante (id, platillo_id, nombre, precio, orden_index) VALUES (gen_random_uuid(), (SELECT id FROM platillo WHERE nombre = 'Pizza Tres Quesos'), 'Grande', 229.00, 1);
INSERT INTO variante (id, platillo_id, nombre, precio, orden_index) VALUES (gen_random_uuid(), (SELECT id FROM platillo WHERE nombre = 'Pizza Tres Quesos'), 'Familiar', 269.00, 2);
INSERT INTO platillo (id, nombre, descripcion, precio, categoria_id, estacion, activo) VALUES (gen_random_uuid(), 'Pizza Mexicana', NULL, NULL, (SELECT id FROM categoria WHERE nombre = 'Pizzas'), 'P2', TRUE);
INSERT INTO variante (id, platillo_id, nombre, precio, orden_index) VALUES (gen_random_uuid(), (SELECT id FROM platillo WHERE nombre = 'Pizza Mexicana'), 'Mediana', 175.00, 0);
INSERT INTO variante (id, platillo_id, nombre, precio, orden_index) VALUES (gen_random_uuid(), (SELECT id FROM platillo WHERE nombre = 'Pizza Mexicana'), 'Grande', 229.00, 1);
INSERT INTO variante (id, platillo_id, nombre, precio, orden_index) VALUES (gen_random_uuid(), (SELECT id FROM platillo WHERE nombre = 'Pizza Mexicana'), 'Familiar', 269.00, 2);
INSERT INTO platillo (id, nombre, descripcion, precio, categoria_id, estacion, activo) VALUES (gen_random_uuid(), 'Pizza Hawaiana', NULL, NULL, (SELECT id FROM categoria WHERE nombre = 'Pizzas'), 'P2', TRUE);
INSERT INTO variante (id, platillo_id, nombre, precio, orden_index) VALUES (gen_random_uuid(), (SELECT id FROM platillo WHERE nombre = 'Pizza Hawaiana'), 'Mediana', 175.00, 0);
INSERT INTO variante (id, platillo_id, nombre, precio, orden_index) VALUES (gen_random_uuid(), (SELECT id FROM platillo WHERE nombre = 'Pizza Hawaiana'), 'Grande', 229.00, 1);
INSERT INTO variante (id, platillo_id, nombre, precio, orden_index) VALUES (gen_random_uuid(), (SELECT id FROM platillo WHERE nombre = 'Pizza Hawaiana'), 'Familiar', 269.00, 2);
INSERT INTO platillo (id, nombre, descripcion, precio, categoria_id, estacion, activo) VALUES (gen_random_uuid(), 'Pizza Peperoni c/ Champiñón', NULL, NULL, (SELECT id FROM categoria WHERE nombre = 'Pizzas'), 'P2', TRUE);
INSERT INTO variante (id, platillo_id, nombre, precio, orden_index) VALUES (gen_random_uuid(), (SELECT id FROM platillo WHERE nombre = 'Pizza Peperoni c/ Champiñón'), 'Mediana', 175.00, 0);
INSERT INTO variante (id, platillo_id, nombre, precio, orden_index) VALUES (gen_random_uuid(), (SELECT id FROM platillo WHERE nombre = 'Pizza Peperoni c/ Champiñón'), 'Grande', 229.00, 1);
INSERT INTO variante (id, platillo_id, nombre, precio, orden_index) VALUES (gen_random_uuid(), (SELECT id FROM platillo WHERE nombre = 'Pizza Peperoni c/ Champiñón'), 'Familiar', 269.00, 2);
INSERT INTO platillo (id, nombre, descripcion, precio, categoria_id, estacion, activo) VALUES (gen_random_uuid(), 'Pizza Vegetariana', NULL, NULL, (SELECT id FROM categoria WHERE nombre = 'Pizzas'), 'P2', TRUE);
INSERT INTO variante (id, platillo_id, nombre, precio, orden_index) VALUES (gen_random_uuid(), (SELECT id FROM platillo WHERE nombre = 'Pizza Vegetariana'), 'Mediana', 175.00, 0);
INSERT INTO variante (id, platillo_id, nombre, precio, orden_index) VALUES (gen_random_uuid(), (SELECT id FROM platillo WHERE nombre = 'Pizza Vegetariana'), 'Grande', 229.00, 1);
INSERT INTO variante (id, platillo_id, nombre, precio, orden_index) VALUES (gen_random_uuid(), (SELECT id FROM platillo WHERE nombre = 'Pizza Vegetariana'), 'Familiar', 269.00, 2);

INSERT INTO platillo (id, nombre, descripcion, precio, categoria_id, estacion, activo) VALUES (gen_random_uuid(), 'Pizza Pastor', NULL, NULL, (SELECT id FROM categoria WHERE nombre = 'Pizzas'), 'P2', TRUE);
INSERT INTO variante (id, platillo_id, nombre, precio, orden_index) VALUES (gen_random_uuid(), (SELECT id FROM platillo WHERE nombre = 'Pizza Pastor'), 'Mediana', 199.00, 0);
INSERT INTO variante (id, platillo_id, nombre, precio, orden_index) VALUES (gen_random_uuid(), (SELECT id FROM platillo WHERE nombre = 'Pizza Pastor'), 'Grande', 249.00, 1);
INSERT INTO variante (id, platillo_id, nombre, precio, orden_index) VALUES (gen_random_uuid(), (SELECT id FROM platillo WHERE nombre = 'Pizza Pastor'), 'Familiar', 289.00, 2);
INSERT INTO platillo (id, nombre, descripcion, precio, categoria_id, estacion, activo) VALUES (gen_random_uuid(), 'Pizza Marinera', 'Camarón, cebolla y aceitunas.', NULL, (SELECT id FROM categoria WHERE nombre = 'Pizzas'), 'P2', TRUE);
INSERT INTO variante (id, platillo_id, nombre, precio, orden_index) VALUES (gen_random_uuid(), (SELECT id FROM platillo WHERE nombre = 'Pizza Marinera'), 'Mediana', 199.00, 0);
INSERT INTO variante (id, platillo_id, nombre, precio, orden_index) VALUES (gen_random_uuid(), (SELECT id FROM platillo WHERE nombre = 'Pizza Marinera'), 'Grande', 249.00, 1);
INSERT INTO variante (id, platillo_id, nombre, precio, orden_index) VALUES (gen_random_uuid(), (SELECT id FROM platillo WHERE nombre = 'Pizza Marinera'), 'Familiar', 289.00, 2);
INSERT INTO platillo (id, nombre, descripcion, precio, categoria_id, estacion, activo) VALUES (gen_random_uuid(), 'Pizza Especial', 'Pastor, tocino y piña.', NULL, (SELECT id FROM categoria WHERE nombre = 'Pizzas'), 'P2', TRUE);
INSERT INTO variante (id, platillo_id, nombre, precio, orden_index) VALUES (gen_random_uuid(), (SELECT id FROM platillo WHERE nombre = 'Pizza Especial'), 'Mediana', 199.00, 0);
INSERT INTO variante (id, platillo_id, nombre, precio, orden_index) VALUES (gen_random_uuid(), (SELECT id FROM platillo WHERE nombre = 'Pizza Especial'), 'Grande', 249.00, 1);
INSERT INTO variante (id, platillo_id, nombre, precio, orden_index) VALUES (gen_random_uuid(), (SELECT id FROM platillo WHERE nombre = 'Pizza Especial'), 'Familiar', 289.00, 2);

INSERT INTO platillo (id, nombre, descripcion, precio, categoria_id, estacion, activo) VALUES (gen_random_uuid(), 'Taco de Pastor', NULL, 17.00, (SELECT id FROM categoria WHERE nombre = 'Tacos'), 'P2', TRUE);
INSERT INTO platillo (id, nombre, descripcion, precio, categoria_id, estacion, activo) VALUES (gen_random_uuid(), 'Taco de Longaniza', NULL, 19.00, (SELECT id FROM categoria WHERE nombre = 'Tacos'), 'P2', TRUE);
INSERT INTO platillo (id, nombre, descripcion, precio, categoria_id, estacion, activo) VALUES (gen_random_uuid(), 'Taco de Bistec', NULL, 19.00, (SELECT id FROM categoria WHERE nombre = 'Tacos'), 'P2', TRUE);
INSERT INTO platillo (id, nombre, descripcion, precio, categoria_id, estacion, activo) VALUES (gen_random_uuid(), 'Taco de Suadero', NULL, 19.00, (SELECT id FROM categoria WHERE nombre = 'Tacos'), 'P2', TRUE);
INSERT INTO platillo (id, nombre, descripcion, precio, categoria_id, estacion, activo) VALUES (gen_random_uuid(), 'Taco de Pastor a la Plancha', NULL, 19.00, (SELECT id FROM categoria WHERE nombre = 'Tacos'), 'P2', TRUE);
INSERT INTO platillo (id, nombre, descripcion, precio, categoria_id, estacion, activo) VALUES (gen_random_uuid(), 'Taco de Cecina', NULL, 25.00, (SELECT id FROM categoria WHERE nombre = 'Tacos'), 'P2', TRUE);
INSERT INTO platillo (id, nombre, descripcion, precio, categoria_id, estacion, activo) VALUES (gen_random_uuid(), 'Taco de Sirlon', NULL, 25.00, (SELECT id FROM categoria WHERE nombre = 'Tacos'), 'P2', TRUE);
INSERT INTO platillo (id, nombre, descripcion, precio, categoria_id, estacion, activo) VALUES (gen_random_uuid(), 'Taco de Arrachera', NULL, 25.00, (SELECT id FROM categoria WHERE nombre = 'Tacos'), 'P2', TRUE);

INSERT INTO platillo (id, nombre, descripcion, precio, categoria_id, estacion, activo) VALUES (gen_random_uuid(), 'Pastor c/ Queso', NULL, 105.00, (SELECT id FROM categoria WHERE nombre = 'Platillos'), 'P2', TRUE);
INSERT INTO platillo (id, nombre, descripcion, precio, categoria_id, estacion, activo) VALUES (gen_random_uuid(), 'Cecina c/ Longaniza y Queso', NULL, 119.00, (SELECT id FROM categoria WHERE nombre = 'Platillos'), 'P2', TRUE);
INSERT INTO platillo (id, nombre, descripcion, precio, categoria_id, estacion, activo) VALUES (gen_random_uuid(), 'Bistec c/ Pastor y Queso', NULL, 115.00, (SELECT id FROM categoria WHERE nombre = 'Platillos'), 'P2', TRUE);
INSERT INTO platillo (id, nombre, descripcion, precio, categoria_id, estacion, activo) VALUES (gen_random_uuid(), 'Bistec c/ Queso', NULL, 110.00, (SELECT id FROM categoria WHERE nombre = 'Platillos'), 'P2', TRUE);
INSERT INTO platillo (id, nombre, descripcion, precio, categoria_id, estacion, activo) VALUES (gen_random_uuid(), 'Arrachera c/ Pastor', NULL, 119.00, (SELECT id FROM categoria WHERE nombre = 'Platillos'), 'P2', TRUE);
INSERT INTO platillo (id, nombre, descripcion, precio, categoria_id, estacion, activo) VALUES (gen_random_uuid(), 'Costilla c/ Guacamole y Cebollitas', NULL, 125.00, (SELECT id FROM categoria WHERE nombre = 'Platillos'), 'P2', TRUE);
INSERT INTO platillo (id, nombre, descripcion, precio, categoria_id, estacion, activo) VALUES (gen_random_uuid(), 'Cecina c/ Guacamole y Cebollitas', NULL, 129.00, (SELECT id FROM categoria WHERE nombre = 'Platillos'), 'P2', TRUE);
INSERT INTO platillo (id, nombre, descripcion, precio, categoria_id, estacion, activo) VALUES (gen_random_uuid(), 'Sirlon c/ Papas a la Francesa', NULL, 129.00, (SELECT id FROM categoria WHERE nombre = 'Platillos'), 'P2', TRUE);

INSERT INTO platillo (id, nombre, descripcion, precio, categoria_id, estacion, activo) VALUES (gen_random_uuid(), 'Alambre Tradicional', 'Bistec, morrón, cebolla, jamón y queso.', 110.00, (SELECT id FROM categoria WHERE nombre = 'Alambres'), 'P2', TRUE);
INSERT INTO platillo (id, nombre, descripcion, precio, categoria_id, estacion, activo) VALUES (gen_random_uuid(), 'Alambre Fortachón', 'Longaniza, pastor, tocino y queso.', 110.00, (SELECT id FROM categoria WHERE nombre = 'Alambres'), 'P2', TRUE);
INSERT INTO platillo (id, nombre, descripcion, precio, categoria_id, estacion, activo) VALUES (gen_random_uuid(), 'Alambre Submarino', 'Bistec, pastor, chorizo, jamón y queso.', 110.00, (SELECT id FROM categoria WHERE nombre = 'Alambres'), 'P2', TRUE);
INSERT INTO platillo (id, nombre, descripcion, precio, categoria_id, estacion, activo) VALUES (gen_random_uuid(), 'Alambre Vegetariano', 'Morrón, cebolla, champiñones, aguacate y queso.', 110.00, (SELECT id FROM categoria WHERE nombre = 'Alambres'), 'P2', TRUE);
INSERT INTO platillo (id, nombre, descripcion, precio, categoria_id, estacion, activo) VALUES (gen_random_uuid(), 'Alambre Sabores', 'Pastor, bistec, longaniza, morrón, cebolla y queso.', 119.00, (SELECT id FROM categoria WHERE nombre = 'Alambres'), 'P2', TRUE);
INSERT INTO platillo (id, nombre, descripcion, precio, categoria_id, estacion, activo) VALUES (gen_random_uuid(), 'Alambre Hawaiano', 'Bistec, piña, jamón, tocino y queso.', 110.00, (SELECT id FROM categoria WHERE nombre = 'Alambres'), 'P2', TRUE);
INSERT INTO platillo (id, nombre, descripcion, precio, categoria_id, estacion, activo) VALUES (gen_random_uuid(), 'Alambre Pingüino', 'Jamón, tocino, bistec y queso.', 110.00, (SELECT id FROM categoria WHERE nombre = 'Alambres'), 'P2', TRUE);
INSERT INTO platillo (id, nombre, descripcion, precio, categoria_id, estacion, activo) VALUES (gen_random_uuid(), 'Alambre Arrachera', 'Arrachera, cebolla y morrón.', 125.00, (SELECT id FROM categoria WHERE nombre = 'Alambres'), 'P2', TRUE);
INSERT INTO platillo (id, nombre, descripcion, precio, categoria_id, estacion, activo) VALUES (gen_random_uuid(), 'Alambre Sirlon', 'Sirlon, morrón y queso.', 125.00, (SELECT id FROM categoria WHERE nombre = 'Alambres'), 'P2', TRUE);

INSERT INTO platillo (id, nombre, descripcion, precio, categoria_id, estacion, activo) VALUES (gen_random_uuid(), 'Torta Cubana', 'Pastor, milanesa, jamón, bistec y queso.', 105.00, (SELECT id FROM categoria WHERE nombre = 'Tortas'), 'P2', TRUE);
INSERT INTO platillo (id, nombre, descripcion, precio, categoria_id, estacion, activo) VALUES (gen_random_uuid(), 'Torta Hawaiana', 'Pastor, jamón, piña, bistec y queso.', 95.00, (SELECT id FROM categoria WHERE nombre = 'Tortas'), 'P2', TRUE);
INSERT INTO platillo (id, nombre, descripcion, precio, categoria_id, estacion, activo) VALUES (gen_random_uuid(), 'Torta Pastor', 'Pastor c/ queso.', 89.00, (SELECT id FROM categoria WHERE nombre = 'Tortas'), 'P2', TRUE);
INSERT INTO platillo (id, nombre, descripcion, precio, categoria_id, estacion, activo) VALUES (gen_random_uuid(), 'Torta Arrachera', 'Arrachera c/ queso.', 115.00, (SELECT id FROM categoria WHERE nombre = 'Tortas'), 'P2', TRUE);
INSERT INTO platillo (id, nombre, descripcion, precio, categoria_id, estacion, activo) VALUES (gen_random_uuid(), 'Torta Arrachera Pastor', 'Arrachera y pastor.', 110.00, (SELECT id FROM categoria WHERE nombre = 'Tortas'), 'P2', TRUE);
INSERT INTO platillo (id, nombre, descripcion, precio, categoria_id, estacion, activo) VALUES (gen_random_uuid(), 'Torta Milanesa', 'Milanesa, jamón y queso.', 95.00, (SELECT id FROM categoria WHERE nombre = 'Tortas'), 'P2', TRUE);
INSERT INTO platillo (id, nombre, descripcion, precio, categoria_id, estacion, activo) VALUES (gen_random_uuid(), 'Torta Bistec', 'Bistec c/ queso.', 95.00, (SELECT id FROM categoria WHERE nombre = 'Tortas'), 'P2', TRUE);
INSERT INTO platillo (id, nombre, descripcion, precio, categoria_id, estacion, activo) VALUES (gen_random_uuid(), 'Torta Suadero', 'Suadero c/ queso.', 115.00, (SELECT id FROM categoria WHERE nombre = 'Tortas'), 'P2', TRUE);

INSERT INTO platillo (id, nombre, descripcion, precio, categoria_id, estacion, activo) VALUES (gen_random_uuid(), 'Alambre Familiar Sabores', NULL, 315.00, (SELECT id FROM categoria WHERE nombre = 'Pá compartir'), 'P2', TRUE);
INSERT INTO platillo (id, nombre, descripcion, precio, categoria_id, estacion, activo) VALUES (gen_random_uuid(), 'Alambre Familiar Tradicional', NULL, 315.00, (SELECT id FROM categoria WHERE nombre = 'Pá compartir'), 'P2', TRUE);
INSERT INTO platillo (id, nombre, descripcion, precio, categoria_id, estacion, activo) VALUES (gen_random_uuid(), 'Alambre Familiar Fortachón', NULL, 315.00, (SELECT id FROM categoria WHERE nombre = 'Pá compartir'), 'P2', TRUE);
INSERT INTO platillo (id, nombre, descripcion, precio, categoria_id, estacion, activo) VALUES (gen_random_uuid(), 'Kilo de Pastor', NULL, 365.00, (SELECT id FROM categoria WHERE nombre = 'Pá compartir'), 'P2', TRUE);
INSERT INTO platillo (id, nombre, descripcion, precio, categoria_id, estacion, activo) VALUES (gen_random_uuid(), 'Kilo de Pastor con Bistec', NULL, 365.00, (SELECT id FROM categoria WHERE nombre = 'Pá compartir'), 'P2', TRUE);
INSERT INTO platillo (id, nombre, descripcion, precio, categoria_id, estacion, activo) VALUES (gen_random_uuid(), 'Kilo de Pastor con Arrachera', NULL, 365.00, (SELECT id FROM categoria WHERE nombre = 'Pá compartir'), 'P2', TRUE);

INSERT INTO platillo (id, nombre, descripcion, precio, categoria_id, estacion, activo) VALUES (gen_random_uuid(), 'Gringa Tradicional', 'Pastor y queso.', 69.00, (SELECT id FROM categoria WHERE nombre = 'Gringas'), 'P2', TRUE);
INSERT INTO platillo (id, nombre, descripcion, precio, categoria_id, estacion, activo) VALUES (gen_random_uuid(), 'Gringa Hawaiana', 'Pastor, jamón, piña y queso.', 79.00, (SELECT id FROM categoria WHERE nombre = 'Gringas'), 'P2', TRUE);
INSERT INTO platillo (id, nombre, descripcion, precio, categoria_id, estacion, activo) VALUES (gen_random_uuid(), 'Beso de Monja', 'Bistec, jamón y queso.', 75.00, (SELECT id FROM categoria WHERE nombre = 'Gringas'), 'P2', TRUE);
INSERT INTO platillo (id, nombre, descripcion, precio, categoria_id, estacion, activo) VALUES (gen_random_uuid(), 'Gringa Bear', 'Arrachera, pastor y queso.', 89.00, (SELECT id FROM categoria WHERE nombre = 'Gringas'), 'P2', TRUE);
INSERT INTO platillo (id, nombre, descripcion, precio, categoria_id, estacion, activo) VALUES (gen_random_uuid(), 'Gringa Yogui', 'Suadero y queso.', 89.00, (SELECT id FROM categoria WHERE nombre = 'Gringas'), 'P2', TRUE);

INSERT INTO platillo (id, nombre, descripcion, precio, categoria_id, estacion, activo) VALUES (gen_random_uuid(), 'Hot-dog Clásico', NULL, 45.00, (SELECT id FROM categoria WHERE nombre = 'Hotdogs y más'), 'P2', TRUE);
INSERT INTO platillo (id, nombre, descripcion, precio, categoria_id, estacion, activo) VALUES (gen_random_uuid(), 'Hot-dog c/ Queso y Tocino', NULL, 59.00, (SELECT id FROM categoria WHERE nombre = 'Hotdogs y más'), 'P2', TRUE);
INSERT INTO platillo (id, nombre, descripcion, precio, categoria_id, estacion, activo) VALUES (gen_random_uuid(), 'Hot-dog c/ Papas y Tocino', NULL, 65.00, (SELECT id FROM categoria WHERE nombre = 'Hotdogs y más'), 'P2', TRUE);
INSERT INTO platillo (id, nombre, descripcion, precio, categoria_id, estacion, activo) VALUES (gen_random_uuid(), 'Papas a la Francesa', NULL, 69.00, (SELECT id FROM categoria WHERE nombre = 'Hotdogs y más'), 'P2', TRUE);
INSERT INTO platillo (id, nombre, descripcion, precio, categoria_id, estacion, activo) VALUES (gen_random_uuid(), 'Papas c/ Queso', NULL, 79.00, (SELECT id FROM categoria WHERE nombre = 'Hotdogs y más'), 'P2', TRUE);
INSERT INTO platillo (id, nombre, descripcion, precio, categoria_id, estacion, activo) VALUES (gen_random_uuid(), 'Papas en Gajos', NULL, 75.00, (SELECT id FROM categoria WHERE nombre = 'Hotdogs y más'), 'P2', TRUE);
INSERT INTO platillo (id, nombre, descripcion, precio, categoria_id, estacion, activo) VALUES (gen_random_uuid(), 'Aros de Cebolla', NULL, 69.00, (SELECT id FROM categoria WHERE nombre = 'Hotdogs y más'), 'P2', TRUE);
INSERT INTO platillo (id, nombre, descripcion, precio, categoria_id, estacion, activo) VALUES (gen_random_uuid(), 'Club Sandwich', NULL, 105.00, (SELECT id FROM categoria WHERE nombre = 'Hotdogs y más'), 'P2', TRUE);
INSERT INTO platillo (id, nombre, descripcion, precio, categoria_id, estacion, activo) VALUES (gen_random_uuid(), 'Dedos de Queso', NULL, 79.00, (SELECT id FROM categoria WHERE nombre = 'Hotdogs y más'), 'P2', TRUE);
INSERT INTO platillo (id, nombre, descripcion, precio, categoria_id, estacion, activo) VALUES (gen_random_uuid(), 'Nachos Sencillo', NULL, 65.00, (SELECT id FROM categoria WHERE nombre = 'Hotdogs y más'), 'P2', TRUE);
INSERT INTO platillo (id, nombre, descripcion, precio, categoria_id, estacion, activo) VALUES (gen_random_uuid(), 'Nachos c/ Longaniza', NULL, 85.00, (SELECT id FROM categoria WHERE nombre = 'Hotdogs y más'), 'P2', TRUE);
INSERT INTO platillo (id, nombre, descripcion, precio, categoria_id, estacion, activo) VALUES (gen_random_uuid(), 'Nachos c/ Cecina', NULL, 110.00, (SELECT id FROM categoria WHERE nombre = 'Hotdogs y más'), 'P2', TRUE);
INSERT INTO platillo (id, nombre, descripcion, precio, categoria_id, estacion, activo) VALUES (gen_random_uuid(), 'Nachos Mixtos', NULL, 110.00, (SELECT id FROM categoria WHERE nombre = 'Hotdogs y más'), 'P2', TRUE);

INSERT INTO platillo (id, nombre, descripcion, precio, categoria_id, estacion, activo) VALUES (gen_random_uuid(), 'Combo Pareja', 'Orden de 12 alas y papas a la francesa.', 215.00, (SELECT id FROM categoria WHERE nombre = 'Combos'), 'P2', TRUE);
INSERT INTO platillo (id, nombre, descripcion, precio, categoria_id, estacion, activo) VALUES (gen_random_uuid(), 'Combo Individual', 'Orden de 6 alas y papas a la francesa.', 119.00, (SELECT id FROM categoria WHERE nombre = 'Combos'), 'P2', TRUE);
INSERT INTO platillo (id, nombre, descripcion, precio, categoria_id, estacion, activo) VALUES (gen_random_uuid(), 'Combo Jocho', 'Hot-dog clásico c/ queso y refresco.', 85.00, (SELECT id FROM categoria WHERE nombre = 'Combos'), 'P2', TRUE);
INSERT INTO platillo (id, nombre, descripcion, precio, categoria_id, estacion, activo) VALUES (gen_random_uuid(), 'Combo Pastor', 'Orden de tacos al pastor y refresco.', 95.00, (SELECT id FROM categoria WHERE nombre = 'Combos'), 'P2', TRUE);
INSERT INTO platillo (id, nombre, descripcion, precio, categoria_id, estacion, activo) VALUES (gen_random_uuid(), 'Combo Bear', 'Hamburguesa clásica c/ papas y refresco.', 105.00, (SELECT id FROM categoria WHERE nombre = 'Combos'), 'P2', TRUE);

INSERT INTO platillo (id, nombre, descripcion, precio, categoria_id, estacion, activo) VALUES (gen_random_uuid(), 'Salsa', NULL, 35.00, (SELECT id FROM categoria WHERE nombre = 'Extras'), 'P2', TRUE);
INSERT INTO platillo (id, nombre, descripcion, precio, categoria_id, estacion, activo) VALUES (gen_random_uuid(), 'Complementos', 'Rábanos, pepino y limón.', 35.00, (SELECT id FROM categoria WHERE nombre = 'Extras'), 'P2', TRUE);
INSERT INTO platillo (id, nombre, descripcion, precio, categoria_id, estacion, activo) VALUES (gen_random_uuid(), 'Cebollitas', NULL, 45.00, (SELECT id FROM categoria WHERE nombre = 'Extras'), 'P2', TRUE);
INSERT INTO platillo (id, nombre, descripcion, precio, categoria_id, estacion, activo) VALUES (gen_random_uuid(), 'Ranch', NULL, 29.00, (SELECT id FROM categoria WHERE nombre = 'Extras'), 'P2', TRUE);
INSERT INTO platillo (id, nombre, descripcion, precio, categoria_id, estacion, activo) VALUES (gen_random_uuid(), 'Extra Salsa', NULL, 29.00, (SELECT id FROM categoria WHERE nombre = 'Extras'), 'P2', TRUE);
INSERT INTO platillo (id, nombre, descripcion, precio, categoria_id, estacion, activo) VALUES (gen_random_uuid(), 'Queso Extra', NULL, 29.00, (SELECT id FROM categoria WHERE nombre = 'Extras'), 'P2', TRUE);

