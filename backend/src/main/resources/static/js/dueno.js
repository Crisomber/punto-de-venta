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
    document.getElementById('platillo-usa-variantes').addEventListener('change', alternarModoVariantes);
    document.getElementById('btn-agregar-variante').addEventListener('click', () => agregarFilaVariante());
    document.getElementById('btn-cancelar-edicion').addEventListener('click', cancelarEdicionPlatillo);
    document.getElementById('usuario-rol').addEventListener('change', actualizarVisibilidadEstacion);
    document.getElementById('form-usuario').addEventListener('submit', crearUsuario);
    document.getElementById('form-password').addEventListener('submit', cambiarPassword);
    document.getElementById('btn-consultar-reporte').addEventListener('click', () =>
        conBotonCargando(document.getElementById('btn-consultar-reporte'), cargarReportes));

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
    if (nombre === 'reportes') {
        return iniciarReportes();
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
        boton.addEventListener('click', () => eliminarMesa(mesa, boton));
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
    await conBotonCargando(evento.submitter, async () => {
        try {
            const numero = parseInt(document.getElementById('mesa-numero').value, 10);
            await apiPostAuth('/api/mesas', { numero });
            document.getElementById('mesa-numero').value = '';
            await cargarMesas();
        } catch (e) {
            error.textContent = e.message;
        }
    });
}

async function eliminarMesa(mesa, boton) {
    if (!confirm('¿Eliminar la mesa ' + mesa.numero + '?')) {
        return;
    }
    const error = document.getElementById('error-mesas');
    error.textContent = '';
    await conBotonCargando(boton, async () => {
        try {
            await apiDeleteAuth('/api/mesas/' + mesa.id);
            await cargarMesas();
        } catch (e) {
            error.textContent = e.message;
        }
    });
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
    await conBotonCargando(evento.submitter, async () => {
        await apiPostAuth('/api/categorias', { nombre: input.value.trim() });
        input.value = '';
        await cargarMenu();
    });
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
    await conBotonCargando(evento.submitter, async () => {
        await apiPostAuth('/api/modificadores', { nombre: input.value.trim() });
        input.value = '';
        await cargarMenu();
    });
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

        const precioTexto = platillo.variantes.length
            ? platillo.variantes.map((v) => v.nombre + ' $' + v.precio).join(' / ')
            : '$' + platillo.precio;
        const texto = document.createElement('span');
        texto.textContent = platillo.nombre + ' — ' + precioTexto + ' — ' + platillo.categoriaNombre +
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
        btnActivo.addEventListener('click', () => cambiarActivoPlatillo(platillo, btnActivo));

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
    document.getElementById('platillo-categoria').value = platillo.categoriaId;
    document.getElementById('platillo-estacion').value = platillo.estacion;
    renderCheckboxesModificadores([...platillo.modificadorIds]);

    document.getElementById('filas-variantes').innerHTML = '';
    if (platillo.variantes.length) {
        document.getElementById('platillo-usa-variantes').checked = true;
        for (const variante of platillo.variantes) {
            agregarFilaVariante(variante.nombre, variante.precio);
        }
        document.getElementById('platillo-precio').value = '';
    } else {
        document.getElementById('platillo-usa-variantes').checked = false;
        document.getElementById('platillo-precio').value = platillo.precio;
    }
    alternarModoVariantes();

    document.getElementById('btn-guardar-platillo').textContent = 'Guardar cambios';
    document.getElementById('btn-cancelar-edicion').classList.remove('oculto');
    document.getElementById('form-platillo').scrollIntoView({ behavior: 'smooth' });
}

function cancelarEdicionPlatillo() {
    editandoPlatilloId = null;
    document.getElementById('titulo-form-platillo').textContent = 'Nuevo platillo';
    document.getElementById('form-platillo').reset();
    renderCheckboxesModificadores([]);
    document.getElementById('filas-variantes').innerHTML = '';
    alternarModoVariantes();
    document.getElementById('btn-guardar-platillo').textContent = 'Crear platillo';
    document.getElementById('btn-cancelar-edicion').classList.add('oculto');
}

function alternarModoVariantes() {
    const usaVariantes = document.getElementById('platillo-usa-variantes').checked;
    document.getElementById('campo-precio').classList.toggle('oculto', usaVariantes);
    document.getElementById('bloque-variantes').classList.toggle('oculto', !usaVariantes);
    if (usaVariantes && document.getElementById('filas-variantes').children.length === 0) {
        agregarFilaVariante();
        agregarFilaVariante();
    }
}

function agregarFilaVariante(nombre, precio) {
    const contenedor = document.getElementById('filas-variantes');
    const fila = document.createElement('div');
    fila.style.cssText = 'display:flex;gap:8px;align-items:center';

    const inputNombre = document.createElement('input');
    inputNombre.type = 'text';
    inputNombre.placeholder = 'Nombre (ej. Grande)';
    inputNombre.className = 'variante-nombre';
    inputNombre.style.flex = '1';
    inputNombre.value = nombre || '';

    const inputPrecio = document.createElement('input');
    inputPrecio.type = 'number';
    inputPrecio.step = '0.01';
    inputPrecio.min = '0.01';
    inputPrecio.placeholder = 'Precio';
    inputPrecio.className = 'variante-precio';
    inputPrecio.style.width = '100px';
    inputPrecio.value = precio ?? '';

    const btnQuitar = document.createElement('button');
    btnQuitar.type = 'button';
    btnQuitar.className = 'boton-mini';
    btnQuitar.textContent = 'Quitar';
    btnQuitar.addEventListener('click', () => fila.remove());

    fila.appendChild(inputNombre);
    fila.appendChild(inputPrecio);
    fila.appendChild(btnQuitar);
    contenedor.appendChild(fila);
}

async function guardarPlatillo(evento) {
    evento.preventDefault();
    const error = document.getElementById('error-platillo');
    error.textContent = '';

    const usaVariantes = document.getElementById('platillo-usa-variantes').checked;
    const cuerpo = {
        nombre: document.getElementById('platillo-nombre').value,
        categoriaId: document.getElementById('platillo-categoria').value,
        estacion: document.getElementById('platillo-estacion').value,
        modificadorIds: [...document.querySelectorAll('#checkboxes-mod-platillo input:checked')]
            .map((c) => c.value),
    };
    if (usaVariantes) {
        cuerpo.variantes = [...document.querySelectorAll('#filas-variantes > div')].map((fila) => ({
            nombre: fila.querySelector('.variante-nombre').value.trim(),
            precio: parseFloat(fila.querySelector('.variante-precio').value),
        }));
    } else {
        cuerpo.precio = parseFloat(document.getElementById('platillo-precio').value);
        cuerpo.variantes = [];
    }

    await conBotonCargando(evento.submitter, async () => {
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
    });
}

async function cambiarActivoPlatillo(platillo, boton) {
    await conBotonCargando(boton, async () => {
        await apiPatchAuth('/api/platillos/' + platillo.id + '/activo', { activo: !platillo.activo });
        await cargarMenu();
    });
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
            btnActivo.addEventListener('click', () => cambiarActivoUsuario(usuario, btnActivo));
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
    await conBotonCargando(evento.submitter, async () => {
        try {
            await apiPostAuth('/api/usuarios', cuerpo);
            document.getElementById('form-usuario').reset();
            actualizarVisibilidadEstacion();
            await cargarUsuarios();
        } catch (e) {
            error.textContent = e.message;
        }
    });
}

async function cambiarActivoUsuario(usuario, boton) {
    await conBotonCargando(boton, async () => {
        await apiPatchAuth('/api/usuarios/' + usuario.id + '/activo', { activo: !usuario.activo });
        await cargarUsuarios();
    });
}

async function cambiarPassword(evento) {
    evento.preventDefault();
    const error = document.getElementById('error-password');
    const exito = document.getElementById('exito-password');
    error.textContent = '';
    exito.textContent = '';
    await conBotonCargando(evento.submitter, async () => {
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
    });
}

// ---------- Reportes ----------

function hoyISO() {
    return new Date().toISOString().slice(0, 10);
}

function formatearFecha(iso) {
    const [anio, mes, dia] = iso.split('-');
    return dia + '/' + mes + '/' + anio;
}

// La fecha se precarga sola con hoy (solo la primera vez que se entra a la
// pestaña) para no obligar al dueno a llenar nada; luego puede cambiarla para
// ver otro dia o un rango, y cada vez que vuelve a la pestana se refresca.
function iniciarReportes() {
    const desde = document.getElementById('reporte-desde');
    const hasta = document.getElementById('reporte-hasta');
    if (!desde.value || !hasta.value) {
        const hoy = hoyISO();
        desde.value = hoy;
        hasta.value = hoy;
    }
    return cargarReportes();
}

async function cargarReportes() {
    const desde = document.getElementById('reporte-desde').value;
    const hasta = document.getElementById('reporte-hasta').value;
    const error = document.getElementById('error-reporte');
    error.textContent = '';
    if (!desde || !hasta) {
        error.textContent = 'Elige un rango de fechas.';
        return;
    }

    const hoy = hoyISO();
    const ventanaDesde = new Date();
    ventanaDesde.setDate(ventanaDesde.getDate() - 6);
    const ventanaDesdeStr = ventanaDesde.toISOString().slice(0, 10);

    try {
        const [totales, ranking, tickets, mesas, ventasPorDia] = await Promise.all([
            apiGet('/api/reportes/ventas?desde=' + desde + '&hasta=' + hasta),
            apiGet('/api/reportes/platillos-mas-vendidos?desde=' + desde + '&hasta=' + hasta),
            apiGet('/api/reportes/tickets?desde=' + desde + '&hasta=' + hasta),
            apiGet('/api/mesas'),
            apiGet('/api/reportes/ventas-por-dia?desde=' + ventanaDesdeStr + '&hasta=' + hoy),
        ]);

        document.getElementById('titulo-reporte-fecha').textContent =
            desde === hasta
                ? (desde === hoy ? 'Hoy' : formatearFecha(desde))
                : formatearFecha(desde) + ' – ' + formatearFecha(hasta);

        renderKpis(totales, tickets, mesas);
        document.getElementById('panel-kpis').classList.remove('oculto');

        renderGraficaIngresos(ventasPorDia, hoy);
        renderGraficaMetodo(totales);
        document.getElementById('fila-graficas').classList.remove('oculto');

        renderGraficaPlatillos(ranking);
        document.getElementById('panel-ranking').classList.remove('oculto');

        renderTickets(tickets);
        document.getElementById('panel-tickets').classList.remove('oculto');
    } catch (e) {
        error.textContent = e.message;
    }
}

function tarjetaKpi(etiqueta, valor, nota) {
    const div = document.createElement('div');
    div.className = 'panel';
    const p1 = document.createElement('p');
    p1.className = 'kpi-etiqueta';
    p1.textContent = etiqueta;
    const p2 = document.createElement('p');
    p2.className = 'kpi-valor';
    p2.textContent = valor;
    div.appendChild(p1);
    div.appendChild(p2);
    if (nota) {
        const span = document.createElement('span');
        span.className = 'kpi-delta';
        span.textContent = nota;
        div.appendChild(span);
    }
    return div;
}

function renderKpis(totales, tickets, mesas) {
    const paraLlevar = tickets.filter((t) => t.mesaNumero == null).length;
    const ocupadas = mesas.filter((m) => m.estado === 'OCUPADA').length;

    const grid = document.getElementById('grid-kpis');
    grid.innerHTML = '';
    grid.appendChild(tarjetaKpi('Total vendido', '$' + totales.totalVendido, null));
    grid.appendChild(tarjetaKpi('Ticket promedio', '$' + totales.ticketPromedio, null));
    grid.appendChild(tarjetaKpi('Cuentas cobradas', String(totales.cantidadCuentas), paraLlevar + ' para llevar'));
    grid.appendChild(tarjetaKpi('Mesas ocupadas', ocupadas + ' / ' + mesas.length, 'Ahora mismo'));
}

// Anima una lista de funciones que ponen el valor final de algo (altura,
// ancho, stroke-dasharray...). Los elementos se dejan en su estado "vacio" al
// insertarlos y, dos frames despues (para que el navegador alcance a pintar
// ese estado inicial), se llama cada funcion — la transicion CSS ya declarada
// en cada elemento hace el resto.
function animarDesdeCero(aplicarCadaUno) {
    requestAnimationFrame(() => {
        requestAnimationFrame(() => {
            for (const aplicar of aplicarCadaUno) {
                aplicar();
            }
        });
    });
}

function renderGraficaIngresos(dias, hoy) {
    const contenedor = document.getElementById('grafica-ingresos');
    contenedor.innerHTML = '';
    const max = Math.max(1, ...dias.map((d) => d.total));
    const animaciones = [];
    for (const dia of dias) {
        const esHoy = dia.fecha === hoy;
        const columna = document.createElement('div');
        columna.className = 'barra-dia' + (esHoy ? ' hoy' : '');

        const relleno = document.createElement('div');
        relleno.className = 'relleno';
        const alturaFinal = Math.max(4, Math.round((dia.total / max) * 130));
        relleno.style.height = '0px';
        animaciones.push(() => { relleno.style.height = alturaFinal + 'px'; });

        const valor = document.createElement('span');
        valor.className = 'valor-dia';
        valor.textContent = '$' + dia.total;
        relleno.appendChild(valor);

        const etiqueta = document.createElement('span');
        etiqueta.className = 'etiqueta-dia';
        etiqueta.textContent = dia.fecha.slice(8, 10) + (esHoy ? ' (hoy)' : '');

        columna.appendChild(relleno);
        columna.appendChild(etiqueta);
        contenedor.appendChild(columna);
    }
    animarDesdeCero(animaciones);
}

function renderGraficaMetodo(totales) {
    const contenedor = document.getElementById('grafica-metodo');
    contenedor.innerHTML = '';

    const total = parseFloat(totales.totalVendido) || 0;
    const efectivo = parseFloat(totales.totalEfectivo) || 0;
    const tarjeta = parseFloat(totales.totalTarjeta) || 0;
    const pctEfectivo = total > 0 ? (efectivo / total) * 100 : 0;
    const pctTarjeta = total > 0 ? (tarjeta / total) * 100 : 0;
    const circunferencia = 2 * Math.PI * 48;
    const largoEfectivo = (pctEfectivo / 100) * circunferencia;
    const largoTarjeta = (pctTarjeta / 100) * circunferencia;

    const svgNS = 'http://www.w3.org/2000/svg';
    const svg = document.createElementNS(svgNS, 'svg');
    svg.setAttribute('width', '120');
    svg.setAttribute('height', '120');
    svg.setAttribute('viewBox', '0 0 120 120');
    svg.setAttribute('role', 'img');
    svg.setAttribute('aria-label',
        'Efectivo ' + pctEfectivo.toFixed(0) + '%, tarjeta ' + pctTarjeta.toFixed(0) + '%');

    const base = document.createElementNS(svgNS, 'circle');
    base.setAttribute('cx', '60');
    base.setAttribute('cy', '60');
    base.setAttribute('r', '48');
    base.setAttribute('fill', 'none');
    base.setAttribute('stroke-width', '18');
    base.style.stroke = 'var(--color-fondo-tabs)';
    svg.appendChild(base);

    if (total > 0) {
        const arcoEfectivo = document.createElementNS(svgNS, 'circle');
        arcoEfectivo.setAttribute('cx', '60');
        arcoEfectivo.setAttribute('cy', '60');
        arcoEfectivo.setAttribute('r', '48');
        arcoEfectivo.setAttribute('fill', 'none');
        arcoEfectivo.setAttribute('stroke-width', '18');
        arcoEfectivo.setAttribute('stroke-dasharray', '0 ' + circunferencia);
        arcoEfectivo.setAttribute('transform', 'rotate(-90 60 60)');
        arcoEfectivo.style.stroke = 'var(--color-acento)';
        arcoEfectivo.style.transition = 'stroke-dasharray .8s cubic-bezier(.22, 1, .36, 1)';
        svg.appendChild(arcoEfectivo);

        const arcoTarjeta = document.createElementNS(svgNS, 'circle');
        arcoTarjeta.setAttribute('cx', '60');
        arcoTarjeta.setAttribute('cy', '60');
        arcoTarjeta.setAttribute('r', '48');
        arcoTarjeta.setAttribute('fill', 'none');
        arcoTarjeta.setAttribute('stroke-width', '18');
        arcoTarjeta.setAttribute('stroke-dasharray', '0 ' + circunferencia);
        arcoTarjeta.setAttribute('stroke-dashoffset', String(-largoEfectivo));
        arcoTarjeta.setAttribute('transform', 'rotate(-90 60 60)');
        arcoTarjeta.style.stroke = 'var(--color-rol-etiqueta)';
        arcoTarjeta.style.transition = 'stroke-dasharray .8s cubic-bezier(.22, 1, .36, 1)';
        svg.appendChild(arcoTarjeta);

        animarDesdeCero([
            () => arcoEfectivo.setAttribute('stroke-dasharray', largoEfectivo + ' ' + circunferencia),
            () => arcoTarjeta.setAttribute('stroke-dasharray', largoTarjeta + ' ' + circunferencia),
        ]);
    }

    contenedor.appendChild(svg);

    const leyenda = document.createElement('div');
    leyenda.style.cssText = 'display:flex;flex-direction:column;gap:10px;font-size:13px';
    leyenda.innerHTML =
        '<span style="display:flex;align-items:center;gap:8px"><span style="width:10px;height:10px;' +
        'border-radius:50%;background:var(--color-acento);display:inline-block"></span>Efectivo — $' +
        totales.totalEfectivo + ' (' + pctEfectivo.toFixed(0) + '%)</span>' +
        '<span style="display:flex;align-items:center;gap:8px"><span style="width:10px;height:10px;' +
        'border-radius:50%;background:var(--color-rol-etiqueta);display:inline-block"></span>Tarjeta — $' +
        totales.totalTarjeta + ' (' + pctTarjeta.toFixed(0) + '%)</span>';
    contenedor.appendChild(leyenda);
}

function renderGraficaPlatillos(ranking) {
    const contenedor = document.getElementById('grafica-platillos');
    contenedor.innerHTML = '';
    const top = ranking.slice(0, 5);
    if (top.length === 0) {
        contenedor.innerHTML = '<p class="vacio">Sin ventas en ese rango.</p>';
        return;
    }
    const max = Math.max(...top.map((p) => p.cantidadVendida));
    const animaciones = [];
    for (const fila of top) {
        const div = document.createElement('div');
        div.className = 'barra-platillo';

        const nombre = document.createElement('span');
        nombre.className = 'nombre';
        nombre.textContent = fila.platilloNombre;

        const pista = document.createElement('div');
        pista.className = 'pista';
        const relleno = document.createElement('div');
        relleno.className = 'relleno';
        const anchoFinal = Math.round((fila.cantidadVendida / max) * 100);
        relleno.style.width = '0%';
        animaciones.push(() => { relleno.style.width = anchoFinal + '%'; });
        pista.appendChild(relleno);

        const cantidad = document.createElement('span');
        cantidad.className = 'cantidad';
        cantidad.textContent = fila.cantidadVendida + ' vendidos';

        div.appendChild(nombre);
        div.appendChild(pista);
        div.appendChild(cantidad);
        contenedor.appendChild(div);
    }
    animarDesdeCero(animaciones);
}

function renderTickets(tickets) {
    document.getElementById('tickets-info').textContent = tickets.length === 0
        ? 'No hay cuentas cobradas en este rango.'
        : 'Mostrando ' + tickets.length + ' cuenta(s) cobrada(s). Toca una para ver el detalle.';

    const grid = document.getElementById('grid-tickets');
    grid.innerHTML = '';
    for (const ticket of tickets) {
        grid.appendChild(cuboTicket(ticket));
    }
}

function cuboTicket(ticket) {
    const boton = document.createElement('button');
    boton.type = 'button';
    boton.className = 'cubo-ticket';

    const esMesa = ticket.mesaNumero != null;
    const cabecera = document.createElement('div');
    cabecera.style.cssText = 'display:flex;justify-content:space-between;align-items:center';
    const pill = document.createElement('span');
    pill.className = 'cubo-pill ' + (esMesa ? 'mesa' : 'para-llevar');
    pill.textContent = esMesa ? 'Mesa ' + ticket.mesaNumero : 'Para llevar';
    const hora = document.createElement('span');
    hora.style.cssText = 'font-size:12px;color:var(--color-texto-muted)';
    hora.textContent = new Date(ticket.fecha).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    cabecera.appendChild(pill);
    cabecera.appendChild(hora);

    const personas = document.createElement('div');
    personas.style.cssText = 'font-size:13px;color:var(--color-texto-secundario);display:flex;' +
        'flex-direction:column;gap:2px';
    personas.innerHTML = '<span>Mesero: ' + ticket.meseroNombre + '</span><span>Cajero: ' +
        ticket.cajeroNombre + '</span>';

    const cantidadPlatillos = ticket.items.reduce((total, item) => total + item.cantidad, 0);
    const pie = document.createElement('div');
    pie.style.cssText = 'display:flex;justify-content:space-between;align-items:baseline;margin-top:4px;' +
        'padding-top:10px;border-top:1px solid var(--color-borde)';
    pie.innerHTML = '<span style="font-size:12px;color:var(--color-texto-muted)">' + cantidadPlatillos +
        ' platillo(s)</span><span style="font-size:20px;font-weight:700">$' + ticket.monto + '</span>';

    const detalle = document.createElement('div');
    detalle.style.cssText = 'display:none;flex-direction:column;gap:6px;margin-top:8px;padding-top:8px;' +
        'border-top:1px solid var(--color-borde)';
    for (const item of ticket.items) {
        const variante = item.varianteNombre ? ' (' + item.varianteNombre + ')' : '';
        const fila = document.createElement('div');
        fila.style.cssText = 'display:flex;justify-content:space-between;font-size:13px;' +
            'color:var(--color-texto-secundario)';
        fila.innerHTML = '<span>' + item.cantidad + 'x ' + item.platilloNombre + variante + '</span><span>$' +
            (item.precioUnitario * item.cantidad).toFixed(2) + '</span>';
        detalle.appendChild(fila);
    }

    boton.appendChild(cabecera);
    boton.appendChild(personas);
    boton.appendChild(pie);
    boton.appendChild(detalle);
    boton.addEventListener('click', () => {
        detalle.style.display = detalle.style.display === 'none' ? 'flex' : 'none';
    });
    return boton;
}

iniciar();
