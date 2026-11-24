let sesion = null;
let ordenEnCobro = null;

async function iniciar() {
    sesion = requerirSesion('CAJERO');
    if (!sesion) {
        return;
    }
    document.getElementById('nombre-usuario').textContent = sesion.nombre;
    document.getElementById('btn-salir').addEventListener('click', cerrarSesion);
    document.getElementById('btn-actualizar').addEventListener('click', cargarPendientes);
    document.getElementById('btn-volver').addEventListener('click', mostrarVistaPendientes);
    document.getElementById('btn-confirmar-cobro').addEventListener('click', confirmarCobro);
    document.getElementById('btn-listo').addEventListener('click', mostrarVistaPendientes);

    await cargarPendientes();
}

function totalEstimado(orden) {
    return orden.items.reduce((total, item) => total + item.precioUnitario * item.cantidad, 0).toFixed(2);
}

async function cargarPendientes() {
    const error = document.getElementById('error-pendientes');
    error.textContent = '';
    try {
        const ordenes = await apiGet('/api/caja/pendientes');
        renderPendientes(ordenes);
    } catch (e) {
        error.textContent = e.message;
    }
}

function renderPendientes(ordenes) {
    const lista = document.getElementById('lista-pendientes');
    lista.innerHTML = '';
    for (const orden of ordenes) {
        const li = document.createElement('li');
        li.className = 'fila-lista';
        const lugar = orden.mesaNumero != null ? 'Mesa ' + orden.mesaNumero : 'Para llevar';
        const texto = document.createElement('span');
        texto.textContent = lugar + ' — ' + orden.items.length + ' platillo(s) — $' + totalEstimado(orden);
        const boton = document.createElement('button');
        boton.textContent = 'Cobrar';
        boton.addEventListener('click', () => mostrarCobro(orden));
        li.appendChild(texto);
        li.appendChild(boton);
        lista.appendChild(li);
    }
    if (ordenes.length === 0) {
        lista.innerHTML = '<p class="vacio">No hay cuentas pendientes de cobro.</p>';
    }
}

function mostrarVistaPendientes() {
    ordenEnCobro = null;
    document.getElementById('vista-cobro').classList.add('oculto');
    document.getElementById('vista-pendientes').classList.remove('oculto');
    cargarPendientes();
}

function mostrarCobro(orden) {
    ordenEnCobro = orden;
    document.getElementById('vista-pendientes').classList.add('oculto');
    document.getElementById('vista-cobro').classList.remove('oculto');
    document.getElementById('panel-metodo').classList.remove('oculto');
    document.getElementById('panel-recibo').classList.add('oculto');
    document.getElementById('error-cobro').textContent = '';

    const lugar = orden.mesaNumero != null ? 'Mesa ' + orden.mesaNumero : 'Para llevar';
    document.getElementById('titulo-cobro').textContent = lugar;
    document.getElementById('total-estimado').textContent = totalEstimado(orden);

    const lista = document.getElementById('lista-items-cobro');
    lista.innerHTML = '';
    for (const item of orden.items) {
        const li = document.createElement('li');
        li.className = 'fila-lista';
        const mods = item.modificadorNombres.length ? ' (' + item.modificadorNombres.join(', ') + ')' : '';
        li.innerHTML = '<span>' + item.cantidad + 'x ' + item.platilloNombre + mods + '</span><span>$' +
            (item.precioUnitario * item.cantidad).toFixed(2) + '</span>';
        lista.appendChild(li);
    }
}

async function confirmarCobro() {
    const metodo = document.querySelector('input[name=metodo]:checked').value;
    const error = document.getElementById('error-cobro');
    error.textContent = '';
    try {
        const recibo = await apiPostAuth('/api/caja/' + ordenEnCobro.id + '/pagar', { metodo });
        mostrarRecibo(recibo);
    } catch (e) {
        error.textContent = e.message;
    }
}

function mostrarRecibo(recibo) {
    document.getElementById('panel-metodo').classList.add('oculto');
    const panel = document.getElementById('panel-recibo');
    panel.classList.remove('oculto');

    const lugar = recibo.mesaNumero != null ? 'Mesa ' + recibo.mesaNumero : 'Para llevar';
    document.getElementById('recibo-detalle').textContent =
        lugar + ' — cobrado por ' + recibo.cajeroNombre + ' el ' + new Date(recibo.fecha).toLocaleString();

    const lista = document.getElementById('recibo-items');
    lista.innerHTML = '';
    for (const item of recibo.items) {
        const li = document.createElement('li');
        li.className = 'fila-lista';
        const mods = item.modificadorNombres.length ? ' (' + item.modificadorNombres.join(', ') + ')' : '';
        li.innerHTML = '<span>' + item.cantidad + 'x ' + item.platilloNombre + mods + '</span>';
        lista.appendChild(li);
    }

    document.getElementById('recibo-monto').textContent = recibo.monto;
    document.getElementById('recibo-metodo').textContent = recibo.metodo;
}

iniciar();
