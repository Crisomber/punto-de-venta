let sesion = null;
let ordenEnCobro = null;

let categorias = [];
let platillos = [];
let categoriaActiva = null;
let carrito = [];

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
    document.getElementById('btn-nueva-venta').addEventListener('click', mostrarVistaMenu);
    document.getElementById('btn-volver-menu').addEventListener('click', mostrarVistaPendientes);
    document.getElementById('btn-cancelar-venta').addEventListener('click', cancelarVenta);
    document.getElementById('btn-cobrar-venta').addEventListener('click', cobrarVentaDirecta);
    document.getElementById('venta-buscar').addEventListener('input', renderGridPlatillos);

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
    document.getElementById('vista-menu').classList.add('oculto');
    document.getElementById('vista-pendientes').classList.remove('oculto');
    cargarPendientes();
}

async function mostrarVistaMenu() {
    document.getElementById('vista-pendientes').classList.add('oculto');
    document.getElementById('vista-menu').classList.remove('oculto');
    document.getElementById('error-venta').textContent = '';

    if (platillos.length === 0) {
        const [listaCategorias, listaPlatillos] = await Promise.all([
            apiGet('/api/categorias'),
            apiGet('/api/platillos'),
        ]);
        categorias = listaCategorias;
        platillos = listaPlatillos.filter((p) => p.activo);
        categoriaActiva = categorias.length ? categorias[0].id : null;
        renderTabsCategorias();
    }
    renderGridPlatillos();
    renderTicket();
}

function renderTabsCategorias() {
    const contenedor = document.getElementById('tabs-categorias');
    contenedor.innerHTML = '';
    for (const categoria of categorias) {
        const boton = document.createElement('button');
        boton.type = 'button';
        boton.className = categoria.id === categoriaActiva ? 'activa' : '';
        boton.textContent = categoria.nombre;
        boton.addEventListener('click', () => {
            categoriaActiva = categoria.id;
            renderTabsCategorias();
            renderGridPlatillos();
        });
        contenedor.appendChild(boton);
    }
}

function renderGridPlatillos() {
    const grid = document.getElementById('grid-platillos');
    grid.innerHTML = '';
    const busqueda = document.getElementById('venta-buscar').value.trim().toLowerCase();
    const visibles = platillos.filter((p) => {
        if (busqueda) {
            return p.nombre.toLowerCase().includes(busqueda);
        }
        return p.categoriaId === categoriaActiva;
    });

    for (const platillo of visibles) {
        const tarjeta = document.createElement('div');
        tarjeta.className = 'tarjeta-platillo';

        const nombre = document.createElement('p');
        nombre.className = 'nombre';
        nombre.textContent = platillo.nombre;
        tarjeta.appendChild(nombre);

        if (platillo.variantes.length) {
            const pills = document.createElement('div');
            pills.className = 'variantes-pills';
            for (const variante of platillo.variantes) {
                const boton = document.createElement('button');
                boton.type = 'button';
                boton.textContent = variante.nombre + ' $' + variante.precio;
                boton.addEventListener('click', () => agregarAlCarrito(platillo, variante));
                pills.appendChild(boton);
            }
            tarjeta.appendChild(pills);
        } else {
            const precio = document.createElement('p');
            precio.className = 'precio';
            precio.textContent = '$' + platillo.precio;
            tarjeta.appendChild(precio);
            tarjeta.style.cursor = 'pointer';
            tarjeta.addEventListener('click', () => agregarAlCarrito(platillo, null));
        }
        grid.appendChild(tarjeta);
    }
    if (visibles.length === 0) {
        grid.innerHTML = '<p class="vacio">No hay platillos en esta categoría.</p>';
    }
}

function agregarAlCarrito(platillo, variante) {
    const varianteId = variante ? variante.id : null;
    const existente = carrito.find((l) => l.platilloId === platillo.id && l.varianteId === varianteId);
    if (existente) {
        existente.cantidad += 1;
    } else {
        carrito.push({
            platilloId: platillo.id,
            nombre: platillo.nombre,
            varianteId,
            varianteNombre: variante ? variante.nombre : null,
            precioUnitario: variante ? variante.precio : platillo.precio,
            cantidad: 1,
        });
    }
    renderTicket();
}

function cambiarCantidadCarrito(linea, delta) {
    linea.cantidad += delta;
    if (linea.cantidad <= 0) {
        carrito = carrito.filter((l) => l !== linea);
    }
    renderTicket();
}

function renderTicket() {
    const lista = document.getElementById('lista-ticket');
    lista.innerHTML = '';
    for (const linea of carrito) {
        const li = document.createElement('li');
        li.className = 'item-fila';

        const info = document.createElement('span');
        info.textContent = linea.nombre + (linea.varianteNombre ? ' (' + linea.varianteNombre + ')' : '');

        const acciones = document.createElement('div');
        acciones.className = 'item-acciones';

        const btnMenos = document.createElement('button');
        btnMenos.type = 'button';
        btnMenos.className = 'boton-mini';
        btnMenos.textContent = '−';
        btnMenos.addEventListener('click', () => cambiarCantidadCarrito(linea, -1));

        const cantidad = document.createElement('span');
        cantidad.textContent = linea.cantidad;

        const btnMas = document.createElement('button');
        btnMas.type = 'button';
        btnMas.className = 'boton-mini';
        btnMas.textContent = '+';
        btnMas.addEventListener('click', () => cambiarCantidadCarrito(linea, 1));

        const subtotal = document.createElement('span');
        subtotal.style.fontWeight = '600';
        subtotal.textContent = '$' + (linea.precioUnitario * linea.cantidad).toFixed(2);

        acciones.appendChild(btnMenos);
        acciones.appendChild(cantidad);
        acciones.appendChild(btnMas);
        acciones.appendChild(subtotal);
        li.appendChild(info);
        li.appendChild(acciones);
        lista.appendChild(li);
    }
    document.getElementById('ticket-vacio').classList.toggle('oculto', carrito.length > 0);
    const total = carrito.reduce((t, l) => t + l.precioUnitario * l.cantidad, 0);
    document.getElementById('ticket-total').textContent = total.toFixed(2);
    document.getElementById('btn-cobrar-venta').disabled = carrito.length === 0;
}

function cancelarVenta() {
    carrito = [];
    mostrarVistaPendientes();
}

async function cobrarVentaDirecta() {
    const error = document.getElementById('error-venta');
    error.textContent = '';
    try {
        const orden = await apiPostAuth('/api/ordenes', { tipo: 'PARA_LLEVAR' });
        const items = carrito.map((linea) => {
            const item = { platilloId: linea.platilloId, cantidad: linea.cantidad };
            if (linea.varianteId) {
                item.varianteId = linea.varianteId;
            }
            return item;
        });
        await apiPostAuth('/api/ordenes/' + orden.id + '/items', { items });
        await apiPostAuth('/api/ordenes/' + orden.id + '/enviar');
        const ordenCerrada = await apiPostAuth('/api/ordenes/' + orden.id + '/cerrar');
        carrito = [];
        document.getElementById('vista-menu').classList.add('oculto');
        mostrarCobro(ordenCerrada);
    } catch (e) {
        error.textContent = e.message;
    }
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
        const variante = item.varianteNombre ? ' (' + item.varianteNombre + ')' : '';
        const mods = item.modificadorNombres.length ? ' (' + item.modificadorNombres.join(', ') + ')' : '';
        li.innerHTML = '<span>' + item.cantidad + 'x ' + item.platilloNombre + variante + mods + '</span><span>$' +
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
        const variante = item.varianteNombre ? ' (' + item.varianteNombre + ')' : '';
        const mods = item.modificadorNombres.length ? ' (' + item.modificadorNombres.join(', ') + ')' : '';
        li.innerHTML = '<span>' + item.cantidad + 'x ' + item.platilloNombre + variante + mods + '</span>';
        lista.appendChild(li);
    }

    document.getElementById('recibo-monto').textContent = recibo.monto;
    document.getElementById('recibo-metodo').textContent = recibo.metodo;
}

iniciar();
