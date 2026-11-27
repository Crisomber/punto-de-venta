let sesion = null;
let categorias = [];
let platillos = [];
let nombreModificadorPorId = {};
let categoriaActiva = null;
let ordenActualId = null;
let pendiente = [];

async function iniciar() {
    sesion = requerirSesion('MESERO');
    if (!sesion) {
        return;
    }
    document.getElementById('nombre-usuario').textContent = sesion.nombre;
    document.getElementById('btn-salir').addEventListener('click', cerrarSesion);
    document.getElementById('btn-volver').addEventListener('click', mostrarVistaMesas);
    document.getElementById('btn-nueva-para-llevar').addEventListener('click', () =>
        conBotonCargando(document.getElementById('btn-nueva-para-llevar'), crearOrdenParaLlevar));
    document.getElementById('orden-buscar').addEventListener('input', renderGridPlatillosOrden);
    document.getElementById('btn-agregar-a-orden').addEventListener('click', () =>
        conBotonCargando(document.getElementById('btn-agregar-a-orden'), agregarPendienteAOrden));
    document.getElementById('btn-enviar').addEventListener('click', () =>
        conBotonCargando(document.getElementById('btn-enviar'), enviarOrden));
    document.getElementById('btn-cerrar').addEventListener('click', () =>
        conBotonCargando(document.getElementById('btn-cerrar'), cerrarCuenta));

    const [listaCategorias, listaPlatillos, listaModificadores] = await Promise.all([
        apiGet('/api/categorias'),
        apiGet('/api/platillos'),
        apiGet('/api/modificadores'),
    ]);
    categorias = listaCategorias;
    platillos = listaPlatillos.filter((p) => p.activo);
    nombreModificadorPorId = Object.fromEntries(listaModificadores.map((m) => [m.id, m.nombre]));
    categoriaActiva = categorias.length ? categorias[0].id : null;
    renderTabsCategoriasOrden();
    await mostrarVistaMesas();
}

// --- Navegacion del menu para agregar platillos (misma idea que la "Nueva
// venta" de caja: buscador + tabs de categoria + tarjetas) ---
function renderTabsCategoriasOrden() {
    const contenedor = document.getElementById('orden-tabs-categorias');
    contenedor.innerHTML = '';
    for (const categoria of categorias) {
        const boton = document.createElement('button');
        boton.type = 'button';
        boton.className = categoria.id === categoriaActiva ? 'activa' : '';
        boton.textContent = categoria.nombre;
        boton.addEventListener('click', () => {
            categoriaActiva = categoria.id;
            renderTabsCategoriasOrden();
            renderGridPlatillosOrden();
        });
        contenedor.appendChild(boton);
    }
}

function renderGridPlatillosOrden() {
    const grid = document.getElementById('orden-grid-platillos');
    grid.innerHTML = '';
    const busqueda = document.getElementById('orden-buscar').value.trim().toLowerCase();
    const visibles = platillos.filter((p) => {
        if (busqueda) {
            return p.nombre.toLowerCase().includes(busqueda);
        }
        return p.categoriaId === categoriaActiva;
    });

    for (const platillo of visibles) {
        const tieneVariantes = platillo.variantes.length > 0;
        const tarjeta = document.createElement(tieneVariantes ? 'div' : 'button');
        tarjeta.className = 'tarjeta-platillo';
        if (!tieneVariantes) {
            tarjeta.type = 'button';
        }

        const nombre = document.createElement('p');
        nombre.className = 'nombre';
        nombre.textContent = platillo.nombre;
        tarjeta.appendChild(nombre);

        if (tieneVariantes) {
            const pills = document.createElement('div');
            pills.className = 'variantes-pills';
            for (const variante of platillo.variantes) {
                const boton = document.createElement('button');
                boton.type = 'button';
                boton.textContent = variante.nombre + ' $' + variante.precio;
                boton.addEventListener('click', () => agregarAPendiente(platillo, variante));
                pills.appendChild(boton);
            }
            tarjeta.appendChild(pills);
        } else {
            const precio = document.createElement('p');
            precio.className = 'precio';
            precio.textContent = '$' + platillo.precio;
            tarjeta.appendChild(precio);
            tarjeta.addEventListener('click', () => agregarAPendiente(platillo, null));
        }
        grid.appendChild(tarjeta);
    }
    if (visibles.length === 0) {
        grid.innerHTML = '<p class="vacio">No hay platillos en esta categoría.</p>';
    }
}

// --- Pedido pendiente: lo que el mesero fue tocando y todavia no se manda
// a la orden real. Aqui tambien se eligen los modificadores (salsas, etc.)
// y la cantidad, antes de confirmar con "Agregar a la orden". ---
function agregarAPendiente(platillo, variante) {
    const varianteId = variante ? variante.id : null;
    const existente = pendiente.find((l) => l.platilloId === platillo.id && l.varianteId === varianteId);
    if (existente) {
        existente.cantidad += 1;
    } else {
        pendiente.push({
            platilloId: platillo.id,
            nombre: platillo.nombre,
            varianteId,
            varianteNombre: variante ? variante.nombre : null,
            modificadorIds: platillo.modificadorIds,
            modificadoresSeleccionados: new Set(),
            cantidad: 1,
        });
    }
    renderPendiente();
}

function cambiarCantidadPendiente(linea, delta) {
    linea.cantidad += delta;
    if (linea.cantidad <= 0) {
        pendiente = pendiente.filter((l) => l !== linea);
    }
    renderPendiente();
}

function renderPendiente() {
    const lista = document.getElementById('lista-pendiente');
    lista.innerHTML = '';
    for (const linea of pendiente) {
        const li = document.createElement('li');
        li.className = 'item-fila';

        const info = document.createElement('div');
        const nombre = document.createElement('span');
        nombre.textContent = linea.nombre + (linea.varianteNombre ? ' (' + linea.varianteNombre + ')' : '');
        info.appendChild(nombre);

        if (linea.modificadorIds.length) {
            const chips = document.createElement('div');
            chips.className = 'modificadores-chips';
            for (const modificadorId of linea.modificadorIds) {
                const chip = document.createElement('button');
                chip.type = 'button';
                const activa = linea.modificadoresSeleccionados.has(modificadorId);
                chip.className = 'chip-modificador' + (activa ? ' activa' : '');
                chip.textContent = nombreModificadorPorId[modificadorId] || '?';
                chip.addEventListener('click', () => {
                    if (linea.modificadoresSeleccionados.has(modificadorId)) {
                        linea.modificadoresSeleccionados.delete(modificadorId);
                    } else {
                        linea.modificadoresSeleccionados.add(modificadorId);
                    }
                    renderPendiente();
                });
                chips.appendChild(chip);
            }
            info.appendChild(chips);
        }

        const acciones = document.createElement('div');
        acciones.className = 'item-acciones';

        const btnMenos = document.createElement('button');
        btnMenos.type = 'button';
        btnMenos.className = 'boton-mini';
        btnMenos.textContent = '−';
        btnMenos.addEventListener('click', () => cambiarCantidadPendiente(linea, -1));

        const cantidad = document.createElement('span');
        cantidad.textContent = linea.cantidad;

        const btnMas = document.createElement('button');
        btnMas.type = 'button';
        btnMas.className = 'boton-mini';
        btnMas.textContent = '+';
        btnMas.addEventListener('click', () => cambiarCantidadPendiente(linea, 1));

        acciones.appendChild(btnMenos);
        acciones.appendChild(cantidad);
        acciones.appendChild(btnMas);
        li.appendChild(info);
        li.appendChild(acciones);
        lista.appendChild(li);
    }
    document.getElementById('pendiente-vacio').classList.toggle('oculto', pendiente.length > 0);
    document.getElementById('btn-agregar-a-orden').disabled = pendiente.length === 0;
}

async function agregarPendienteAOrden() {
    const error = document.getElementById('error-agregar');
    error.textContent = '';
    const items = pendiente.map((linea) => {
        const item = {
            platilloId: linea.platilloId,
            cantidad: linea.cantidad,
            modificadorIds: [...linea.modificadoresSeleccionados],
        };
        if (linea.varianteId) {
            item.varianteId = linea.varianteId;
        }
        return item;
    });
    try {
        await apiPostAuth('/api/ordenes/' + ordenActualId + '/items', { items });
        pendiente = [];
        renderPendiente();
        await refrescarOrden();
    } catch (e) {
        error.textContent = e.message;
    }
}

async function mostrarVistaMesas() {
    ordenActualId = null;
    document.getElementById('vista-orden').classList.add('oculto');
    document.getElementById('vista-mesas').classList.remove('oculto');
    document.getElementById('error-mesas').textContent = '';

    const [mesas, ordenes] = await Promise.all([apiGet('/api/mesas'), apiGet('/api/ordenes')]);
    renderMesas(mesas, ordenes);
    renderParaLlevar(ordenes);
}

function renderMesas(mesas, ordenes) {
    const grid = document.getElementById('grid-mesas');
    grid.innerHTML = '';
    const ordenadas = [...mesas].sort((a, b) => a.numero - b.numero);
    for (const mesa of ordenadas) {
        const boton = document.createElement('button');
        boton.type = 'button';
        boton.className = 'boton-mesa ' + (mesa.estado === 'OCUPADA' ? 'ocupada' : 'libre');
        boton.textContent = 'Mesa ' + mesa.numero;
        boton.addEventListener('click', () => conBotonCargando(boton, () => clickMesa(mesa, ordenes)));
        grid.appendChild(boton);
    }
    if (ordenadas.length === 0) {
        grid.innerHTML = '<p class="vacio">No hay mesas registradas.</p>';
    }
}

function renderParaLlevar(ordenes) {
    const lista = document.getElementById('lista-para-llevar');
    lista.innerHTML = '';
    const abiertas = ordenes.filter((o) => o.mesaNumero == null && (o.estado === 'ABIERTA' || o.estado === 'ENVIADA'));
    for (const orden of abiertas) {
        const li = document.createElement('li');
        li.className = 'fila-lista';
        const texto = document.createElement('span');
        texto.textContent = 'Orden para llevar — ' + orden.items.length + ' platillo(s) — ' + orden.estado;
        const boton = document.createElement('button');
        boton.textContent = 'Abrir';
        boton.addEventListener('click', () => conBotonCargando(boton, () => mostrarOrden(orden.id)));
        li.appendChild(texto);
        li.appendChild(boton);
        lista.appendChild(li);
    }
    if (abiertas.length === 0) {
        lista.innerHTML = '<p class="vacio">No hay órdenes para llevar abiertas.</p>';
    }
}

async function clickMesa(mesa, ordenes) {
    const error = document.getElementById('error-mesas');
    error.textContent = '';
    if (mesa.estado === 'LIBRE') {
        try {
            const orden = await apiPostAuth('/api/ordenes', { tipo: 'CONSUMO_LOCAL', mesaId: mesa.id });
            await mostrarOrden(orden.id);
        } catch (e) {
            error.textContent = e.message;
        }
        return;
    }
    const orden = ordenes.find((o) => o.mesaNumero === mesa.numero && (o.estado === 'ABIERTA' || o.estado === 'ENVIADA'));
    if (orden) {
        await mostrarOrden(orden.id);
    } else {
        error.textContent = 'No se encontró una orden abierta para esa mesa.';
    }
}

async function crearOrdenParaLlevar() {
    const error = document.getElementById('error-mesas');
    error.textContent = '';
    try {
        const orden = await apiPostAuth('/api/ordenes', { tipo: 'PARA_LLEVAR' });
        await mostrarOrden(orden.id);
    } catch (e) {
        error.textContent = e.message;
    }
}

async function mostrarOrden(id) {
    ordenActualId = id;
    pendiente = [];
    document.getElementById('orden-buscar').value = '';
    categoriaActiva = categorias.length ? categorias[0].id : categoriaActiva;
    renderTabsCategoriasOrden();
    renderGridPlatillosOrden();
    renderPendiente();
    document.getElementById('vista-mesas').classList.add('oculto');
    document.getElementById('vista-orden').classList.remove('oculto');
    document.getElementById('error-orden').textContent = '';
    document.getElementById('error-agregar').textContent = '';
    await refrescarOrden();
}

async function refrescarOrden() {
    const orden = await apiGet('/api/ordenes/' + ordenActualId);
    renderOrden(orden);
}

function renderOrden(orden) {
    const lugar = orden.mesaNumero != null ? 'Mesa ' + orden.mesaNumero : 'Para llevar';
    document.getElementById('titulo-orden').textContent = lugar + ' — ' + orden.estado;

    const lista = document.getElementById('lista-items');
    lista.innerHTML = '';
    for (const item of orden.items) {
        const li = document.createElement('li');
        li.className = 'fila-lista';
        const variante = item.varianteNombre ? ' (' + item.varianteNombre + ')' : '';
        const mods = item.modificadorNombres.length ? ' (' + item.modificadorNombres.join(', ') + ')' : '';
        li.innerHTML = '<span>' + item.cantidad + 'x ' + item.platilloNombre + variante + mods +
            '</span><span class="estado-pill">' + item.estado + '</span>';
        lista.appendChild(li);
    }
    if (orden.items.length === 0) {
        lista.innerHTML = '<p class="vacio">Sin platillos todavía.</p>';
    }

    const puedeAgregar = orden.estado === 'ABIERTA' || orden.estado === 'ENVIADA';
    document.getElementById('panel-agregar-platillos').classList.toggle('oculto', !puedeAgregar);
    document.getElementById('bloque-pendiente').classList.toggle('oculto', !puedeAgregar);
    document.getElementById('btn-enviar').classList.toggle('oculto', orden.estado !== 'ABIERTA');
    document.getElementById('btn-cerrar').classList.toggle('oculto', orden.estado !== 'ENVIADA');
}

async function enviarOrden() {
    const error = document.getElementById('error-orden');
    error.textContent = '';
    try {
        await apiPostAuth('/api/ordenes/' + ordenActualId + '/enviar');
        await refrescarOrden();
    } catch (e) {
        error.textContent = e.message;
    }
}

async function cerrarCuenta() {
    const error = document.getElementById('error-orden');
    error.textContent = '';
    try {
        await apiPostAuth('/api/ordenes/' + ordenActualId + '/cerrar');
        await mostrarVistaMesas();
    } catch (e) {
        error.textContent = e.message;
    }
}

iniciar();
