let sesion = null;
let categorias = [];
let modificadores = [];
let editandoPlatilloId = null;

async function iniciar() {
    sesion = requerirSesion('DUENO');
    if (!sesion) {
        return;
    }
    document.getElementById('nombre-usuario').textContent = sesion.nombre;
    document.getElementById('btn-salir').addEventListener('click', cerrarSesion);

    document.getElementById('tab-mesas').addEventListener('click', () => mostrarPestana('mesas'));
    document.getElementById('tab-menu').addEventListener('click', () => mostrarPestana('menu'));
    document.getElementById('tab-usuarios').addEventListener('click', () => mostrarPestana('usuarios'));
    document.getElementById('tab-reportes').addEventListener('click', () => mostrarPestana('reportes'));

    document.getElementById('form-mesa').addEventListener('submit', crearMesa);
    document.getElementById('form-categoria').addEventListener('submit', crearCategoria);
    document.getElementById('form-modificador').addEventListener('submit', crearModificador);
    document.getElementById('form-platillo').addEventListener('submit', guardarPlatillo);
    document.getElementById('btn-cancelar-edicion').addEventListener('click', cancelarEdicionPlatillo);
    document.getElementById('usuario-rol').addEventListener('change', actualizarVisibilidadEstacion);
    document.getElementById('form-usuario').addEventListener('submit', crearUsuario);
    document.getElementById('form-password').addEventListener('submit', cambiarPassword);
    document.getElementById('btn-consultar-reporte').addEventListener('click', consultarReporte);

    actualizarVisibilidadEstacion();
    await mostrarPestana('mesas');
}

function mostrarPestana(nombre) {
    for (const n of ['mesas', 'menu', 'usuarios', 'reportes']) {
        document.getElementById('tab-' + n).classList.toggle('activa', n === nombre);
        document.getElementById('vista-' + n).classList.toggle('oculto', n !== nombre);
    }
    if (nombre === 'mesas') {
        return cargarMesas();
    }
    if (nombre === 'menu') {
        return cargarMenu();
    }
    if (nombre === 'usuarios') {
        return cargarUsuarios();
    }
}

// ---------- Mesas ----------

async function cargarMesas() {
    const mesas = await apiGet('/api/mesas');
    const lista = document.getElementById('lista-mesas');
    lista.innerHTML = '';
    for (const mesa of [...mesas].sort((a, b) => a.numero - b.numero)) {
        const li = document.createElement('li');
        li.className = 'fila-lista';
        const texto = document.createElement('span');
        texto.textContent = 'Mesa ' + mesa.numero + ' — ' + mesa.estado;
        const boton = document.createElement('button');
        boton.textContent = 'Eliminar';
        boton.addEventListener('click', () => eliminarMesa(mesa));
        li.appendChild(texto);
        li.appendChild(boton);
        lista.appendChild(li);
    }
    if (mesas.length === 0) {
        lista.innerHTML = '<p class="vacio">No hay mesas registradas.</p>';
    }
}

async function crearMesa(evento) {
    evento.preventDefault();
    const error = document.getElementById('error-mesas');
    error.textContent = '';
    try {
        const numero = parseInt(document.getElementById('mesa-numero').value, 10);
        await apiPostAuth('/api/mesas', { numero });
        document.getElementById('mesa-numero').value = '';
        await cargarMesas();
    } catch (e) {
        error.textContent = e.message;
    }
}

async function eliminarMesa(mesa) {
    if (!confirm('¿Eliminar la mesa ' + mesa.numero + '?')) {
        return;
    }
    const error = document.getElementById('error-mesas');
    error.textContent = '';
    try {
        await apiDeleteAuth('/api/mesas/' + mesa.id);
        await cargarMesas();
    } catch (e) {
        error.textContent = e.message;
    }
}

// ---------- Menu: categorias, modificadores, platillos ----------

async function cargarMenu() {
    const [listaCategorias, listaModificadores, listaPlatillos] = await Promise.all([
        apiGet('/api/categorias'),
        apiGet('/api/modificadores'),
        apiGet('/api/platillos'),
    ]);
    categorias = listaCategorias;
    modificadores = listaModificadores;
    renderCategorias();
    renderModificadores();
    renderPlatillos(listaPlatillos);
    renderSelectCategorias();
    renderCheckboxesModificadores([]);
}

function renderCategorias() {
    const lista = document.getElementById('lista-categorias');
    lista.innerHTML = '';
    for (const categoria of categorias) {
        const li = document.createElement('li');
        li.className = 'fila-lista';
        li.innerHTML = '<span>' + categoria.nombre + '</span>';
        lista.appendChild(li);
    }
    if (categorias.length === 0) {
        lista.innerHTML = '<p class="vacio">Sin categorías todavía.</p>';
    }
}

async function crearCategoria(evento) {
    evento.preventDefault();
    const input = document.getElementById('categoria-nombre');
    if (!input.value.trim()) {
        return;
    }
    await apiPostAuth('/api/categorias', { nombre: input.value.trim() });
    input.value = '';
    await cargarMenu();
}

function renderModificadores() {
    const lista = document.getElementById('lista-modificadores');
    lista.innerHTML = '';
    for (const modificador of modificadores) {
        const li = document.createElement('li');
        li.className = 'fila-lista';
        li.innerHTML = '<span>' + modificador.nombre + '</span>';
        lista.appendChild(li);
    }
    if (modificadores.length === 0) {
        lista.innerHTML = '<p class="vacio">Sin modificadores todavía.</p>';
    }
}

async function crearModificador(evento) {
    evento.preventDefault();
    const input = document.getElementById('modificador-nombre');
    if (!input.value.trim()) {
        return;
    }
    await apiPostAuth('/api/modificadores', { nombre: input.value.trim() });
    input.value = '';
    await cargarMenu();
}

function renderSelectCategorias() {
    const select = document.getElementById('platillo-categoria');
    const valorActual = select.value;
    select.innerHTML = '';
    for (const categoria of categorias) {
        const opcion = document.createElement('option');
        opcion.value = categoria.id;
        opcion.textContent = categoria.nombre;
        select.appendChild(opcion);
    }
    if (valorActual) {
        select.value = valorActual;
    }
}

function renderCheckboxesModificadores(seleccionados) {
    const contenedor = document.getElementById('checkboxes-mod-platillo');
    contenedor.innerHTML = '';
    for (const modificador of modificadores) {
        const etiqueta = document.createElement('label');
        const casilla = document.createElement('input');
        casilla.type = 'checkbox';
        casilla.value = modificador.id;
        casilla.checked = seleccionados.includes(modificador.id);
        etiqueta.appendChild(casilla);
        etiqueta.appendChild(document.createTextNode(modificador.nombre));
        contenedor.appendChild(etiqueta);
    }
}

function renderPlatillos(platillos) {
    const lista = document.getElementById('lista-platillos');
    lista.innerHTML = '';
    for (const platillo of platillos) {
        const li = document.createElement('li');
        li.className = 'fila-lista';

        const texto = document.createElement('span');
        texto.textContent = platillo.nombre + ' — $' + platillo.precio + ' — ' + platillo.categoriaNombre +
            ' — ' + platillo.estacion;

        const acciones = document.createElement('div');
        acciones.style.display = 'flex';
        acciones.style.gap = '6px';

        const pill = document.createElement('span');
        pill.className = 'estado-pill';
        pill.textContent = platillo.activo ? 'ACTIVO' : 'INACTIVO';

        const btnEditar = document.createElement('button');
        btnEditar.className = 'boton-mini';
        btnEditar.textContent = 'Editar';
        btnEditar.addEventListener('click', () => cargarPlatilloEnFormulario(platillo));

        const btnActivo = document.createElement('button');
        btnActivo.className = 'boton-mini';
        btnActivo.textContent = platillo.activo ? 'Desactivar' : 'Activar';
        btnActivo.addEventListener('click', () => cambiarActivoPlatillo(platillo));

        acciones.appendChild(pill);
        acciones.appendChild(btnEditar);
        acciones.appendChild(btnActivo);

        li.appendChild(texto);
        li.appendChild(acciones);
        lista.appendChild(li);
    }
    if (platillos.length === 0) {
        lista.innerHTML = '<p class="vacio">Sin platillos todavía.</p>';
    }
}

function cargarPlatilloEnFormulario(platillo) {
    editandoPlatilloId = platillo.id;
    document.getElementById('titulo-form-platillo').textContent = 'Editar platillo';
    document.getElementById('platillo-nombre').value = platillo.nombre;
    document.getElementById('platillo-precio').value = platillo.precio;
    document.getElementById('platillo-categoria').value = platillo.categoriaId;
    document.getElementById('platillo-estacion').value = platillo.estacion;
    renderCheckboxesModificadores([...platillo.modificadorIds]);
    document.getElementById('btn-guardar-platillo').textContent = 'Guardar cambios';
    document.getElementById('btn-cancelar-edicion').classList.remove('oculto');
    document.getElementById('form-platillo').scrollIntoView({ behavior: 'smooth' });
}

function cancelarEdicionPlatillo() {
    editandoPlatilloId = null;
    document.getElementById('titulo-form-platillo').textContent = 'Nuevo platillo';
    document.getElementById('form-platillo').reset();
    renderCheckboxesModificadores([]);
    document.getElementById('btn-guardar-platillo').textContent = 'Crear platillo';
    document.getElementById('btn-cancelar-edicion').classList.add('oculto');
}

async function guardarPlatillo(evento) {
    evento.preventDefault();
    const error = document.getElementById('error-platillo');
    error.textContent = '';

    const cuerpo = {
        nombre: document.getElementById('platillo-nombre').value,
        precio: parseFloat(document.getElementById('platillo-precio').value),
        categoriaId: document.getElementById('platillo-categoria').value,
        estacion: document.getElementById('platillo-estacion').value,
        modificadorIds: [...document.querySelectorAll('#checkboxes-mod-platillo input:checked')]
            .map((c) => c.value),
    };

    try {
        if (editandoPlatilloId) {
            await apiPatchAuth('/api/platillos/' + editandoPlatilloId, cuerpo);
        } else {
            await apiPostAuth('/api/platillos', cuerpo);
        }
        cancelarEdicionPlatillo();
        await cargarMenu();
    } catch (e) {
        error.textContent = e.message;
    }
}

async function cambiarActivoPlatillo(platillo) {
    await apiPatchAuth('/api/platillos/' + platillo.id + '/activo', { activo: !platillo.activo });
    await cargarMenu();
}

// ---------- Usuarios ----------

function actualizarVisibilidadEstacion() {
    const esChef = document.getElementById('usuario-rol').value === 'CHEF';
    document.getElementById('campo-estacion-usuario').classList.toggle('oculto', !esChef);
}

async function cargarUsuarios() {
    const usuarios = await apiGet('/api/usuarios');
    const lista = document.getElementById('lista-usuarios');
    lista.innerHTML = '';
    for (const usuario of usuarios) {
        const li = document.createElement('li');
        li.className = 'fila-lista';

        const detalle = usuario.rol === 'DUENO' ? usuario.username : (usuario.estacion || '—');
        const texto = document.createElement('span');
        texto.textContent = usuario.nombre + ' — ' + usuario.rol + ' (' + detalle + ')';

        const acciones = document.createElement('div');
        acciones.style.display = 'flex';
        acciones.style.gap = '6px';

        const pill = document.createElement('span');
        pill.className = 'estado-pill';
        pill.textContent = usuario.activo ? 'ACTIVO' : 'INACTIVO';
        acciones.appendChild(pill);

        if (usuario.rol !== 'DUENO') {
            const btnActivo = document.createElement('button');
            btnActivo.className = 'boton-mini';
            btnActivo.textContent = usuario.activo ? 'Desactivar' : 'Activar';
            btnActivo.addEventListener('click', () => cambiarActivoUsuario(usuario));
            acciones.appendChild(btnActivo);
        }

        li.appendChild(texto);
        li.appendChild(acciones);
        lista.appendChild(li);
    }
}

async function crearUsuario(evento) {
    evento.preventDefault();
    const error = document.getElementById('error-usuario');
    error.textContent = '';
    const rol = document.getElementById('usuario-rol').value;
    const cuerpo = {
        nombre: document.getElementById('usuario-nombre').value,
        rol,
        estacion: rol === 'CHEF' ? document.getElementById('usuario-estacion').value : null,
        pin: document.getElementById('usuario-pin').value,
    };
    try {
        await apiPostAuth('/api/usuarios', cuerpo);
        document.getElementById('form-usuario').reset();
        actualizarVisibilidadEstacion();
        await cargarUsuarios();
    } catch (e) {
        error.textContent = e.message;
    }
}

async function cambiarActivoUsuario(usuario) {
    await apiPatchAuth('/api/usuarios/' + usuario.id + '/activo', { activo: !usuario.activo });
    await cargarUsuarios();
}

async function cambiarPassword(evento) {
    evento.preventDefault();
    const error = document.getElementById('error-password');
    const exito = document.getElementById('exito-password');
    error.textContent = '';
    exito.textContent = '';
    try {
        await apiPatchAuth('/api/auth/password', {
            passwordActual: document.getElementById('password-actual').value,
            passwordNueva: document.getElementById('password-nueva').value,
        }, { cerrarSesionSi401: false });
        document.getElementById('form-password').reset();
        exito.textContent = 'Contraseña actualizada.';
    } catch (e) {
        error.textContent = e.message;
    }
}

// ---------- Reportes ----------

async function consultarReporte() {
    const desde = document.getElementById('reporte-desde').value;
    const hasta = document.getElementById('reporte-hasta').value;
    const error = document.getElementById('error-reporte');
    error.textContent = '';
    if (!desde || !hasta) {
        error.textContent = 'Elige un rango de fechas.';
        return;
    }
    try {
        const [totales, ranking] = await Promise.all([
            apiGet('/api/reportes/ventas?desde=' + desde + '&hasta=' + hasta),
            apiGet('/api/reportes/platillos-mas-vendidos?desde=' + desde + '&hasta=' + hasta),
        ]);
        document.getElementById('r-total-vendido').textContent = '$' + totales.totalVendido;
        document.getElementById('r-total-efectivo').textContent = '$' + totales.totalEfectivo;
        document.getElementById('r-total-tarjeta').textContent = '$' + totales.totalTarjeta;
        document.getElementById('r-ticket-promedio').textContent = '$' + totales.ticketPromedio;
        document.getElementById('r-cantidad-cuentas').textContent = totales.cantidadCuentas;
        document.getElementById('panel-totales').classList.remove('oculto');

        const lista = document.getElementById('lista-ranking');
        lista.innerHTML = '';
        for (const fila of ranking) {
            const li = document.createElement('li');
            li.className = 'fila-lista';
            li.innerHTML = '<span>' + fila.platilloNombre + '</span><span>' + fila.cantidadVendida + ' vendidos</span>';
            lista.appendChild(li);
        }
        if (ranking.length === 0) {
            lista.innerHTML = '<p class="vacio">Sin ventas en ese rango.</p>';
        }
        document.getElementById('panel-ranking').classList.remove('oculto');
    } catch (e) {
        error.textContent = e.message;
    }
}

iniciar();
