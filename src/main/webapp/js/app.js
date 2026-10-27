'use strict';

// Ruta relativa: funciona con cualquier nombre de aplicación y desde cualquier dispositivo
const API = 'api/tasks';
const STATUSES = ['Pending', 'In Progress', 'Completed'];

const $ = (id) => document.getElementById(id);
const dialog = $('task-dialog');

let currentFilter = '';   // '' = todas
let toastTimer = null;
let pressStartedOnBackdrop = false;

// Iconos: SVG fijo escrito aquí (no contiene datos del usuario, por eso es seguro usar innerHTML)
const SVG_ATTRS = 'viewBox="0 0 20 20" width="16" height="16" aria-hidden="true" focusable="false" ' +
    'fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"';
const ICON_EDIT = '<svg ' + SVG_ATTRS + '><path d="M13.5 3.5l3 3L7 16H4v-3z"/></svg>';
const ICON_DELETE = '<svg ' + SVG_ATTRS + '><path d="M4 6h12M8 6V4h4v2M6 6l.8 10.5h6.4L14 6"/></svg>';
const ICON_EMPTY = '<svg viewBox="0 0 48 48" width="48" height="48" aria-hidden="true" focusable="false" ' +
    'fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">' +
    '<rect x="10" y="8" width="28" height="34" rx="4"/><path d="M18 8V6h12v2M17 22l3 3 6-6M17 33h14"/></svg>';

// ---------- Comunicación con el backend ----------

async function request(url, options = {}) {
    let res;
    try {
        res = await fetch(url, {
            headers: { 'Content-Type': 'application/json' },
            ...options
        });
    } catch (e) {
        throw new Error('Could not reach the server. Make sure the application is running.');
    }

    if (res.status === 204) return null;      // DELETE no devuelve cuerpo

    let data = null;
    try { data = await res.json(); } catch (e) { /* la respuesta no era JSON */ }

    if (!res.ok) {
        throw new Error((data && data.error) || 'Unexpected error (' + res.status + ')');
    }
    return data;
}

// ---------- Mensajes ----------

// Si la ventana del formulario está abierta, el error se muestra dentro de ella
function showError(message) {
    const id = dialog.open ? 'form-error' : 'error';
    $(id + '-text').textContent = message;
    $(id).hidden = false;
}

function clearError() {
    $('error').hidden = true;
    $('form-error').hidden = true;
}

// Aviso breve que desaparece solo
function showToast(message) {
    const toast = $('toast');
    toast.textContent = message;
    toast.hidden = false;
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => { toast.hidden = true; }, 2500);
}

// ---------- Utilidades para construir el DOM ----------
// Para texto del usuario se usa textContent (nunca innerHTML), así no se ejecuta como HTML.

function el(tag, className, text) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text) node.textContent = text;
    return node;
}

function iconButton(icon, className, title, label) {
    const button = el('button', 'icon-btn ' + className);
    button.type = 'button';
    button.title = title;
    button.setAttribute('aria-label', label);
    button.innerHTML = icon;
    return button;
}

function slug(text) {
    return text.toLowerCase().replace(/\s+/g, '-');
}

function formatDay(iso) {
    const date = new Date(iso);
    return isNaN(date) ? '' : date.toLocaleDateString('en-US', { day: 'numeric', month: 'short', year: 'numeric' });
}

function formatDate(iso) {
    const date = new Date(iso);
    return isNaN(date) ? '' : date.toLocaleString('en-US', { dateStyle: 'medium', timeStyle: 'short' });
}

// ---------- Listado ----------

async function loadTasks() {
    try {
        const url = currentFilter ? API + '?status=' + encodeURIComponent(currentFilter) : API;
        renderTasks(await request(url));
    } catch (e) {
        showError(e.message);
    }
}

function renderTasks(tasks) {
    const list = $('task-list');
    list.innerHTML = '';

    $('count').textContent = tasks.length;
    $('count').hidden = false;
    $('list-head').hidden = (tasks.length === 0);

    if (tasks.length === 0) {
        list.appendChild(createEmptyState());
        return;
    }
    tasks.forEach((task) => list.appendChild(createTaskItem(task)));
}

function createEmptyState() {
    const li = el('li', 'empty');

    const icon = el('div', 'empty-icon');
    icon.innerHTML = ICON_EMPTY;
    li.appendChild(icon);

    if (currentFilter) {
        li.appendChild(el('p', 'empty-title', 'No results'));
        li.appendChild(el('p', null, 'No tasks with status ' + currentFilter + '.'));
    } else {
        li.appendChild(el('p', 'empty-title', 'No tasks yet'));
        li.appendChild(el('p', null, "Create the first one to start organizing your team's work."));
        const button = el('button', 'btn btn-primary', 'Create task');
        button.type = 'button';
        button.addEventListener('click', openCreate);
        li.appendChild(button);
    }
    return li;
}

function createTaskItem(task) {
    const li = el('li', 'task');
    if (task.status === 'Completed') li.classList.add('is-completed');

    // Columna 1: título y descripción
    const main = el('div', 'cell-main');
    main.appendChild(el('h3', 'task-title', task.title));
    if (task.description) {
        const desc = el('p', 'task-desc', task.description);
        desc.title = task.description;   // el texto completo se ve al pasar el cursor
        main.appendChild(desc);
    }

    // Columnas 2 a 4: prioridad, estado y fecha
    const meta = el('div', 'task-meta');

    const prio = el('span', 'prio prio-' + task.priority.toLowerCase());
    prio.appendChild(el('span', 'sr-only', 'Priority '));
    prio.appendChild(document.createTextNode(task.priority));
    meta.appendChild(prio);

    const select = el('select', 'status-select status-' + slug(task.status));
    select.setAttribute('aria-label', 'Status of task ' + task.title);
    STATUSES.forEach((status) => {
        const option = el('option', null, status);
        option.value = status;
        option.selected = (status === task.status);
        select.appendChild(option);
    });
    // Cambiar el estado: se reenvía la tarea completa porque PUT reemplaza todos los campos
    select.addEventListener('change', () => {
        save(task.id, {
            title: task.title,
            description: task.description,
            priority: task.priority,
            status: select.value
        }, 'Status updated');
    });
    meta.appendChild(select);

    const date = el('span', 'cell-date', formatDay(task.createdAt));
    date.title = formatDate(task.createdAt);
    meta.appendChild(date);

    // Columna 5: acciones
    const cell = el('div', 'cell-actions');
    const actions = el('div', 'task-actions');

    const editBtn = iconButton(ICON_EDIT, '', 'Edit', 'Edit task ' + task.title);
    editBtn.addEventListener('click', () => startEdit(task));
    actions.appendChild(editBtn);

    const deleteBtn = iconButton(ICON_DELETE, 'icon-btn-danger', 'Delete', 'Delete task ' + task.title);
    actions.appendChild(deleteBtn);

    // Confirmación de borrado dentro de la propia fila (reemplaza a las acciones)
    const confirmBox = el('div', 'task-confirm');
    confirmBox.hidden = true;
    confirmBox.appendChild(el('span', null, 'Delete this task?'));

    const yesBtn = el('button', 'btn btn-danger-solid btn-small', 'Delete');
    yesBtn.type = 'button';
    yesBtn.addEventListener('click', () => removeTask(task));
    confirmBox.appendChild(yesBtn);

    const noBtn = el('button', 'btn btn-secondary btn-small', 'Cancel');
    noBtn.type = 'button';
    noBtn.addEventListener('click', () => {
        confirmBox.hidden = true;
        actions.hidden = false;
        li.classList.remove('confirming');
        deleteBtn.focus();
    });
    confirmBox.appendChild(noBtn);

    deleteBtn.addEventListener('click', () => {
        actions.hidden = true;
        confirmBox.hidden = false;
        li.classList.add('confirming');
        noBtn.focus();   // el foco empieza en la opción segura
    });

    cell.appendChild(actions);
    cell.appendChild(confirmBox);

    li.appendChild(main);
    li.appendChild(meta);
    li.appendChild(cell);
    return li;
}

// ---------- Crear / Editar / Eliminar ----------

// Si recibe id hace PUT (editar); si no, POST (crear). Devuelve true si salió bien.
async function save(id, data, successMessage) {
    let ok = false;
    try {
        clearError();
        if (id) {
            await request(API + '/' + id, { method: 'PUT', body: JSON.stringify(data) });
        } else {
            await request(API, { method: 'POST', body: JSON.stringify(data) });
        }
        ok = true;
    } catch (e) {
        showError(e.message);
    }
    await loadTasks();   // siempre se vuelve a pedir la lista al servidor
    if (ok) showToast(successMessage);
    return ok;
}

async function removeTask(task) {
    let ok = false;
    try {
        clearError();
        await request(API + '/' + task.id, { method: 'DELETE' });
        ok = true;
    } catch (e) {
        showError(e.message);
    }
    await loadTasks();
    if (ok) showToast('Task deleted');
}

// ---------- Ventana del formulario ----------

// Ajusta títulos y botones según se esté creando o editando
function updateFormMode() {
    const editing = Boolean($('task-id').value);
    $('form-title').textContent = editing ? 'Edit task #' + $('task-id').value : 'New task';
    $('submit-btn').textContent = editing ? 'Save changes' : 'Create task';
}

function updateCounters() {
    $('title-count').textContent = $('title').value.length + '/' + $('title').maxLength;
    $('description-count').textContent = $('description').value.length + '/' + $('description').maxLength;
}

// Muestra (o quita, si no hay mensaje) el error junto al campo del título
function setTitleError(message) {
    const hasError = Boolean(message);
    $('title-error').textContent = message || '';
    $('title-error').hidden = !hasError;
    $('title-field').classList.toggle('is-invalid', hasError);
    $('title').setAttribute('aria-invalid', String(hasError));
}

function resetForm() {
    $('task-form').reset();
    $('task-id').value = '';
    setTitleError('');
    updateFormMode();
    updateCounters();
}

function openCreate() {
    resetForm();
    clearError();
    dialog.showModal();
    $('title').focus();
}

function startEdit(task) {
    resetForm();
    clearError();
    $('task-id').value = task.id;
    $('title').value = task.title;
    $('description').value = task.description || '';
    $('priority').value = task.priority;
    $('status').value = task.status;
    updateFormMode();
    updateCounters();
    dialog.showModal();
    $('title').focus();
}

function setSaving(saving) {
    $('submit-btn').disabled = saving;
    if (saving) {
        $('submit-btn').textContent = 'Saving…';
    } else {
        updateFormMode();
    }
}

// ---------- Eventos ----------

$('task-form').addEventListener('submit', async (event) => {
    event.preventDefault();   // evita que la página se recargue

    const title = $('title').value.trim();
    if (!title) {
        setTitleError('Please enter a title for the task.');
        $('title').focus();
        return;
    }
    setTitleError('');

    const editing = Boolean($('task-id').value);
    setSaving(true);
    const ok = await save($('task-id').value, {
        title: title,
        description: $('description').value.trim(),
        priority: $('priority').value,
        status: $('status').value
    }, editing ? 'Changes saved' : 'Task created');
    setSaving(false);

    if (ok) dialog.close();   // si falló, la ventana sigue abierta con lo escrito para poder corregirlo
});

$('title').addEventListener('input', () => {
    updateCounters();
    if ($('title').value.trim()) setTitleError('');
});
$('description').addEventListener('input', updateCounters);

$('new-btn').addEventListener('click', openCreate);
$('cancel').addEventListener('click', () => dialog.close());
$('dialog-close').addEventListener('click', () => dialog.close());

// Al cerrar la ventana (botones, tecla Escape o clic fuera) se limpia el formulario
dialog.addEventListener('close', resetForm);

// Clic en el fondo oscuro cierra la ventana (solo si el clic empezó y terminó allí,
// para no perder lo escrito al soltar el ratón fuera después de seleccionar texto)
dialog.addEventListener('mousedown', (event) => { pressStartedOnBackdrop = (event.target === dialog); });
dialog.addEventListener('click', (event) => {
    if (event.target === dialog && pressStartedOnBackdrop) dialog.close();
});

$('error-close').addEventListener('click', clearError);

document.querySelectorAll('.chip').forEach((chip) => {
    chip.addEventListener('click', () => {
        currentFilter = chip.dataset.status;
        document.querySelectorAll('.chip').forEach((other) => {
            const active = (other === chip);
            other.classList.toggle('is-active', active);
            other.setAttribute('aria-pressed', String(active));
        });
        loadTasks();
    });
});

// Al abrir la página se cargan las tareas
updateCounters();
loadTasks();