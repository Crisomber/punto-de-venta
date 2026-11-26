let sesion = null;
let platillos = [];
let nombreModificadorPorId = {};
let ordenActualId = null;

async function iniciar() {
    sesion = requerirSesion('MESERO');
    if (!sesion) {
        return;
    }
    document.getElementById('nombre-usuario').textContent = sesion.nombre;
    document.getElementById('btn-salir').addEventListener('click', cerrarSesion);
    document.getElementById('btn-volver').addEventListener('click', mostrarVistaMesas);
    document.getElementById('btn-nueva-para-llevar').addEventListener('click', crearOrdenParaLlevar);
    document.getElementById('select-platillo').addEventListener('change', () => {
        renderModificadoresDisponibles();
        renderVarianteDisponible();
    });
    document.getElementById('form-agregar-item').addEventListener('submit', agregarItem);
    document.getElementById('btn-enviar').addEventListener('click', enviarOrden);
    document.getElementById('btn-cerrar').addEventListener('click', cerrarCuenta);

    const [listaPlatillos, listaModificadores] = await Promise.all([
        apiGet('/api/platillos'),
        apiGet('/api/modificadores'),
    ]);
    platillos = listaPlatillos.filter((p) => p.activo);
    nombreModificadorPorId = Object.fromEntries(listaModificadores.map((m) => [m.id, m.nombre]));
    llenarSelectPlatillos();
    await mostrarVistaMesas();
}

function llenarSelectPlatillos() {
    const select = document.getElementById('select-platillo');
    select.innerHTML = '';
    for (const platillo of platillos) {
        const opcion = document.createElement('option');
        opcion.value = platillo.id;
        const precioTexto = platillo.variantes.length ? 'variantes' : '$' + platillo.precio;
        opcion.textContent = platillo.nombre + ' — ' + precioTexto + ' (' + platillo.categoriaNombre + ')';
        select.appendChild(opcion);
    }
    renderModificadoresDisponibles();
    renderVarianteDisponible();
}

function renderVarianteDisponible() {
    const contenedor = document.getElementById('selector-variante');
    const select = document.getElementById('select-variante');
    select.innerHTML = '';
    const platilloId = document.getElementById('select-platillo').value;
    const platillo = platillos.find((p) => p.id === platilloId);
    if (!platillo || !platillo.variantes.length) {
        contenedor.classList.add('oculto');
        return;
    }
    contenedor.classList.remove('oculto');
    for (const variante of platillo.variantes) {
        const opcion = document.createElement('option');
        opcion.value = variante.id;
        opcion.textContent = variante.nombre + ' — $' + variante.precio;
        select.appendChild(opcion);
    }
}

function renderModificadoresDisponibles() {
    const contenedor = document.getElementById('checkboxes-modificadores');
    contenedor.innerHTML = '';
    const platilloId = document.getElementById('select-platillo').value;
    const platillo = platillos.find((p) => p.id === platilloId);
    if (!platillo) {
        return;
    }
    for (const modificadorId of platillo.modificadorIds) {
        const etiqueta = document.createElement('label');
        const casilla = document.createElement('input');
        casilla.type = 'checkbox';
        casilla.value = modificadorId;
        etiqueta.appendChild(casilla);
        etiqueta.appendChild(document.createTextNode(nombreModificadorPorId[modificadorId] || '?'));
        contenedor.appendChild(etiqueta);
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
        boton.addEventListener('click', () => clickMesa(mesa, ordenes));
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
        boton.addEventListener('click', () => mostrarOrden(orden.id));
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
    document.getElementById('panel-agregar').classList.toggle('oculto', !puedeAgregar);
    document.getElementById('btn-enviar').classList.toggle('oculto', orden.estado !== 'ABIERTA');
    document.getElementById('btn-cerrar').classList.toggle('oculto', orden.estado !== 'ENVIADA');
}

async function agregarItem(evento) {
    evento.preventDefault();
    const platilloId = document.getElementById('select-platillo').value;
    const cantidad = parseInt(document.getElementById('input-cantidad').value, 10);
    const modificadorIds = [...document.querySelectorAll('#checkboxes-modificadores input:checked')]
        .map((casilla) => casilla.value);
    const error = document.getElementById('error-agregar');
    error.textContent = '';
    const item = { platilloId, cantidad, modificadorIds };
    const platillo = platillos.find((p) => p.id === platilloId);
    if (platillo && platillo.variantes.length) {
        item.varianteId = document.getElementById('select-variante').value;
    }
    try {
        await apiPostAuth('/api/ordenes/' + ordenActualId + '/items', {
            items: [item],
        });
        await refrescarOrden();
    } catch (e) {
        error.textContent = e.message;
    }
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
