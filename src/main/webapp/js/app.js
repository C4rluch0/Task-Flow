'use strict';

// Ruta relativa: funciona con cualquier nombre de aplicación y desde cualquier dispositivo
const API = 'api/tasks';
const STATUSES = ['Pending', 'In Progress', 'Completed'];

const $ = (id) => document.getElementById(id);

let currentFilter = '';   // '' = todas
let toastTimer = null;

// ---------- Comunicación con el backend ----------

async function request(url, options = {}) {
    let res;
    try {
        res = await fetch(url, {
            headers: { 'Content-Type': 'application/json' },
            ...options
        });
    } catch (e) {
        throw new Error('No se pudo conectar con el servidor. Comprueba que la aplicación esté en ejecución.');
    }

    if (res.status === 204) return null;      // DELETE no devuelve cuerpo

    let data = null;
    try { data = await res.json(); } catch (e) { /* la respuesta no era JSON */ }

    if (!res.ok) {
        throw new Error((data && data.error) || 'Error inesperado (' + res.status + ')');
    }
    return data;
}

// ---------- Mensajes ----------

function showError(message) {
    $('error-text').textContent = message;
    $('error').hidden = false;
}

function clearError() {
    $('error').hidden = true;
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
// Se usa textContent (nunca innerHTML) para que el texto del usuario no se ejecute como HTML.

function el(tag, className, text) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text) node.textContent = text;
    return node;
}

function slug(text) {
    return text.toLowerCase().replace(/\s+/g, '-');
}

function formatDate(iso) {
    const date = new Date(iso);
    return isNaN(date) ? '' : date.toLocaleString('es', { dateStyle: 'medium', timeStyle: 'short' });
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

    if (tasks.length === 0) {
        const message = currentFilter
            ? 'No hay tareas con estado ' + currentFilter + '.'
            : 'Todavía no hay tareas. Crea la primera con el formulario.';
        list.appendChild(el('li', 'empty', message));
        return;
    }

    tasks.forEach((task) => list.appendChild(createTaskItem(task)));
}

function createTaskItem(task) {
    const li = el('li', 'task prio-' + task.priority.toLowerCase());
    if (task.status === 'Completed') li.classList.add('is-completed');

    // Información de la tarea
    const main = el('div', 'task-main');
    main.appendChild(el('h3', 'task-title', task.title));
    if (task.description) {
        main.appendChild(el('p', 'task-desc', task.description));
    }
    const meta = el('p', 'task-meta');
    meta.appendChild(el('span', null, 'Prioridad: ' + task.priority));
    const created = formatDate(task.createdAt);
    if (created) meta.appendChild(el('span', null, 'Creada: ' + created));
    main.appendChild(meta);

    // Acciones normales
    const actions = el('div', 'task-actions');

    const select = el('select', 'status-select status-' + slug(task.status));
    select.setAttribute('aria-label', 'Estado de la tarea ' + task.title);
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
        }, 'Estado actualizado');
    });
    actions.appendChild(select);

    const editBtn = el('button', 'btn btn-secondary btn-small', 'Editar');
    editBtn.type = 'button';
    editBtn.addEventListener('click', () => startEdit(task));
    actions.appendChild(editBtn);

    const deleteBtn = el('button', 'btn btn-secondary btn-small btn-danger', 'Eliminar');
    deleteBtn.type = 'button';
    actions.appendChild(deleteBtn);

    // Confirmación de borrado dentro de la propia fila (reemplaza a las acciones)
    const confirmBox = el('div', 'task-confirm');
    confirmBox.hidden = true;
    confirmBox.appendChild(el('span', null, '¿Eliminar esta tarea?'));

    const yesBtn = el('button', 'btn btn-danger-solid btn-small', 'Eliminar');
    yesBtn.type = 'button';
    yesBtn.addEventListener('click', () => removeTask(task));
    confirmBox.appendChild(yesBtn);

    const noBtn = el('button', 'btn btn-secondary btn-small', 'Cancelar');
    noBtn.type = 'button';
    noBtn.addEventListener('click', () => {
        confirmBox.hidden = true;
        actions.hidden = false;
        deleteBtn.focus();
    });
    confirmBox.appendChild(noBtn);

    deleteBtn.addEventListener('click', () => {
        actions.hidden = true;
        confirmBox.hidden = false;
        noBtn.focus();   // el foco empieza en la opción segura
    });

    li.appendChild(main);
    li.appendChild(actions);
    li.appendChild(confirmBox);
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
        if ($('task-id').value === String(task.id)) resetForm();   // se estaba editando esa tarea
    } catch (e) {
        showError(e.message);
    }
    await loadTasks();
    if (ok) showToast('Tarea eliminada');
}

// ---------- Formulario ----------

// Ajusta títulos y botones según se esté creando o editando
function updateFormMode() {
    const editing = Boolean($('task-id').value);
    $('form-title').textContent = editing ? 'Editar tarea #' + $('task-id').value : 'Nueva tarea';
    $('submit-btn').textContent = editing ? 'Guardar cambios' : 'Crear tarea';
    $('cancel').hidden = !editing;
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

function startEdit(task) {
    $('task-id').value = task.id;
    $('title').value = task.title;
    $('description').value = task.description || '';
    $('priority').value = task.priority;
    $('status').value = task.status;
    setTitleError('');
    updateFormMode();
    updateCounters();
    $('form-title').scrollIntoView({ behavior: 'smooth', block: 'start' });
    $('title').focus();
}

function resetForm() {
    $('task-form').reset();
    $('task-id').value = '';
    setTitleError('');
    updateFormMode();
    updateCounters();
}

function setSaving(saving) {
    $('submit-btn').disabled = saving;
    if (saving) {
        $('submit-btn').textContent = 'Guardando…';
    } else {
        updateFormMode();
    }
}

// ---------- Eventos ----------

$('task-form').addEventListener('submit', async (event) => {
    event.preventDefault();   // evita que la página se recargue

    const title = $('title').value.trim();
    if (!title) {
        setTitleError('Escribe un título para la tarea.');
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
    }, editing ? 'Cambios guardados' : 'Tarea creada');
    setSaving(false);

    if (ok) resetForm();   // si falló, se conserva lo escrito para poder corregirlo
});

// Escape cancela la edición
$('task-form').addEventListener('keydown', (event) => {
    if (event.key === 'Escape' && $('task-id').value) resetForm();
});

$('title').addEventListener('input', () => {
    updateCounters();
    if ($('title').value.trim()) setTitleError('');
});
$('description').addEventListener('input', updateCounters);

$('cancel').addEventListener('click', resetForm);
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