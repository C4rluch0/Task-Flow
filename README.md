# TaskFlow

Aplicación web para administrar las tareas de un equipo (MVP del *Technical Challenge — Web Full Stack Developer*).
Permite **listar, crear, editar, eliminar, cambiar el estado y filtrar tareas por estado**.

| Capa | Tecnología |
|---|---|
| Frontend | HTML, CSS y JavaScript puro (sin frameworks) |
| Backend | Java 21+, Jakarta Servlet 6.1, JDBC, Gson |
| Servidor | Apache Tomcat 11 (lo descarga y arranca Maven automáticamente) |
| Base de datos | H2 en modo archivo (persistente) |
| Pruebas | JUnit 5 |
| Construcción | Maven |

---

## Requisitos

- **JDK 21 o superior** (probado con JDK 25).
- **Maven 3.9 o superior**.
- Conexión a internet la primera vez (Maven descarga las dependencias y Tomcat).

Comprueba tu instalación con `java -version` y `mvn -version`.

## Cómo ejecutar

Desde la carpeta del proyecto:

```
mvn clean package cargo:run
```

Cuando la consola muestre `Tomcat 11.0.7 started on port [8080]`, abre:

**http://localhost:8080/TaskFlow/**

Para detener el servidor, pulsa `Ctrl+C` en la terminal.

Notas:

- La base de datos se crea sola en `data/taskflow.mv.db` la primera vez que arranca la aplicación (junto con la tabla `tasks`). No hay que ejecutar ningún script.
- Los datos **se conservan** al reiniciar la aplicación e incluso después de `mvn clean`, porque la base vive en `data` y no en `target`.
- Las pruebas se ejecutan automáticamente con `package`. Para arrancar sin ejecutarlas: `mvn clean package cargo:run -DskipTests`.

## Ejecutar las pruebas

```
mvn test
```

Son 31 pruebas automatizadas:

| Clase | Pruebas | Qué verifica |
|---|---|---|
| `TaskValidatorTest` | 15 | Validación de datos: obligatorios, límites de longitud, valores por defecto y rechazos |
| `TaskEnumsTest` | 5 | Conversión y rechazo de estados y prioridades |
| `TaskDaoTest` | 11 | CRUD, filtro por estado, orden, inyección SQL y restricciones, contra H2 real |

Las pruebas del DAO usan una base aparte (`target/test-db`) y nunca tocan los datos de `data`.

## Ver la base de datos

La base se puede abrir con cualquier cliente JDBC mientras la aplicación corre:

- **URL:** `jdbc:h2:<ruta-del-proyecto>/data/taskflow;AUTO_SERVER=TRUE`
- **Usuario:** `sa`
- **Contraseña:** *(vacía)*

Por ejemplo, en IntelliJ: *Database → New → Data Source → H2 → Embedded*.

---

## Modelo de datos

| Campo | Tipo | Reglas |
|---|---|---|
| `id` | número | Generado automáticamente |
| `title` | texto | **Obligatorio**, máximo 200 caracteres |
| `description` | texto | Opcional, máximo 1000 caracteres |
| `status` | texto | `Pending` (por defecto), `In Progress`, `Completed` |
| `priority` | texto | `Low`, `Medium` (por defecto), `High` |
| `createdAt` | fecha ISO-8601 | Asignada por la base de datos al crear |

## API REST

Base: `http://localhost:8080/TaskFlow/api/tasks`

| Operación | Método | URL | Respuesta correcta |
|---|---|---|---|
| Listar | `GET` | `/api/tasks` | `200` + lista JSON |
| Filtrar por estado | `GET` | `/api/tasks?status=In Progress` | `200` + lista JSON |
| Consultar una | `GET` | `/api/tasks/{id}` | `200` + tarea |
| Crear | `POST` | `/api/tasks` | `201` + tarea creada |
| Actualizar | `PUT` | `/api/tasks/{id}` | `200` + tarea actualizada |
| Eliminar | `DELETE` | `/api/tasks/{id}` | `204` sin cuerpo |

`PUT` reemplaza todos los campos editables (`title`, `description`, `status`, `priority`).

**Errores.** Siempre devuelven JSON con la forma `{"error": "mensaje"}`:

| Código | Cuándo |
|---|---|
| `400` | JSON inválido, título vacío o demasiado largo, estado o prioridad no permitidos, ID mal formado |
| `404` | La tarea no existe |
| `500` | Error inesperado de base de datos (el detalle queda en el log del servidor, no se expone al cliente) |

**Ejemplo** (PowerShell):

```powershell
curl.exe -X POST http://localhost:8080/TaskFlow/api/tasks -H "Content-Type: application/json" -d '{\"title\":\"Preparar demo\",\"priority\":\"High\"}'
```

Respuesta:

```json
{"id":1,"title":"Preparar demo","status":"Pending","priority":"High","createdAt":"2026-10-23T17:30:12.345"}
```

---

## Estructura del proyecto

```
TaskFlow/
├── pom.xml
├── data/                          ← base de datos H2 (se crea sola, no se versiona)
└── src/
    ├── main/
    │   ├── java/org/example/
    │   │   ├── model/             Task, TaskStatus, TaskPriority
    │   │   ├── validation/        TaskValidator (reglas de validación)
    │   │   ├── dao/               TaskDao (todo el SQL)
    │   │   ├── db/                Database (conexión), AppInitializer (crea la tabla)
    │   │   └── servlet/           TaskServlet (API REST)
    │   └── webapp/
    │       ├── index.html         Estructura de la página
    │       ├── css/style.css      Estilos
    │       └── js/app.js          Lógica del cliente (llamadas a la API y manejo de la interfaz)
    └── test/java/org/example/     Pruebas JUnit
```

Flujo de una petición:

```
Navegador (fetch + JSON) → TaskServlet → TaskValidator → TaskDao → H2
```

---

## Decisiones técnicas

- **Servlets + JDBC, sin frameworks.** El reto pide un MVP sencillo y bien estructurado. Usar la API estándar de Jakarta hace visible cada paso (recibir la petición, validar, persistir, responder) y reduce dependencias. El costo es algo más de código repetitivo que con Spring.
- **Capas separadas (servlet → validador → DAO).** Cada clase tiene una sola responsabilidad. El servlet solo maneja HTTP, el DAO concentra todo el SQL y el validador se puede probar sin base de datos ni servidor.
- **Validación en dos niveles.** `TaskValidator` rechaza datos inválidos con un `400` y un mensaje claro. Además, la tabla tiene restricciones `CHECK` en `status` y `priority`, de modo que la base de datos se protege incluso si alguien se salta la validación. Hay una prueba para cada nivel.
- **Valores por defecto vs. valores inválidos.** Si `status` o `priority` no se envían, se usan `Pending` y `Medium`. Si se envían con un valor no permitido, se rechazan: no se corrigen en silencio.
- **Enums para estado y prioridad.** Evitan cadenas "mágicas" repartidas por el código. El texto que viaja en el JSON (`In Progress`) coincide con el de la base de datos.
- **`PreparedStatement` en todas las consultas**, para evitar inyección SQL. Hay una prueba que guarda un intento de inyección como texto plano.
- **H2 en modo archivo.** Cumple el requisito de persistencia sin instalar un motor externo. `AUTO_SERVER=TRUE` permite que la aplicación y un cliente de base de datos usen el mismo archivo a la vez. Para producción convendría PostgreSQL.
- **La ruta de la base de datos se inyecta desde el `pom.xml`** (propiedad `tf.db.path`, apuntando a `./data` del proyecto). Cargo ejecuta Tomcat con una carpeta de trabajo dentro de `target`, por lo que una ruta relativa se borraría con `mvn clean`. Orden de prioridad: propiedad `tf.db.path` → variable de entorno `TF_DB_PATH` → `./data/taskflow`.
- **Carga explícita del driver (`Class.forName("org.h2.Driver")`).** Dentro de Tomcat, el driver vive en `WEB-INF/lib` y `DriverManager` no lo detecta por sí solo; sin esto la aplicación fallaba al arrancar con `No suitable driver`.
- **Cargo para ejecutar con un solo comando.** El evaluador no necesita instalar ni configurar Tomcat.
- **Frontend sin frameworks, dividido en HTML, CSS y JavaScript**, con rutas relativas (`api/tasks`). Funciona sin importar el nombre del servidor y desde otro dispositivo de la red, y no depende de internet (tipografía del sistema, sin librerías externas).
- **`textContent` en lugar de `innerHTML`** al mostrar datos del usuario, para impedir ataques XSS.
- **Se recarga la lista desde el servidor después de cada cambio**, en lugar de modificar la pantalla a mano, de modo que lo mostrado siempre coincide con lo guardado.
- **Interfaz sobria y responsive.** Una columna en móvil y dos (formulario + lista) desde 820 px. Toda la paleta vive en variables CSS, lo que permitió añadir un **modo oscuro automático** (según la preferencia del sistema) sin duplicar estilos. Se verificó por cálculo que los pares de colores de texto cumplen el contraste mínimo de WCAG (4,5:1) en ambos modos.
- **El color comunica información.** La prioridad se indica con una franja a la izquierda de cada fila (y también con texto, para no depender solo del color) y el estado con una etiqueta de color que a la vez es el selector para cambiarlo.
- **Retroalimentación al usuario.** Validación del título junto al propio campo, contadores de caracteres, avisos breves al crear, editar o eliminar, y mensajes de error que explican qué falló. La confirmación de borrado se hace dentro de la fila, con el foco en la opción segura (Cancelar), en lugar del cuadro del navegador.
- **Accesibilidad básica:** foco visible para teclado, `Escape` cancela la edición, atributos ARIA en filtros y errores, y se respeta la preferencia de movimiento reducido.
- **El formulario conserva lo escrito si el servidor rechaza los datos**, para que se pueda corregir sin volver a teclear, y deshabilita el botón mientras guarda para evitar envíos dobles.
- **`createdAt` como texto ISO-8601.** Evita problemas de Gson con las clases `java.time` en Java 17+.

---

## Funcionalidades

### Completadas (requisitos obligatorios)

- [x] Listar tareas (título, descripción, estado, prioridad y fecha de creación)
- [x] Crear tareas con formulario y validaciones
- [x] Editar tareas
- [x] Eliminar tareas (con confirmación)
- [x] Actualizar el estado (Pending / In Progress / Completed) directamente desde la lista
- [x] Filtrar el listado por estado
- [x] API REST con CRUD, validación de datos y códigos HTTP con manejo de errores
- [x] Base de datos persistente (los datos sobreviven a reinicios)
- [x] Interfaz responsive, con modo oscuro automático
- [x] Pruebas automatizadas (31)

### Pendientes / no incluidas

- [ ] Búsqueda de tareas por título *(opcional)*
- [ ] Indicadores o estadísticas de tareas *(opcional)*
- [ ] Pruebas automatizadas de los servlets y del frontend (hoy se cubren indirectamente con las pruebas del validador y el DAO, y manualmente)
- [ ] Paginación del listado
- [ ] Autenticación (el reto no la exige)
- [ ] Credenciales de base de datos seguras para producción (hoy usa `sa` sin contraseña, solo apto para desarrollo local)

---

## Solución de problemas

| Síntoma | Causa probable | Solución |
|---|---|---|
| `mvn` no se reconoce | Maven no está instalado o no está en el `PATH` | Instalar Maven 3.9+ o usar el Maven integrado del IDE |
| Error `Address already in use` | El puerto 8080 está ocupado | Cerrar el otro programa o un Tomcat anterior |
| `404` en `/TaskFlow/` | La aplicación no arrancó | Revisar la consola en busca de líneas `GRAVE` |
| `Wrong user name or password` | `data/taskflow.mv.db` fue creado por otro programa con otro usuario | Detener la app, borrar `data/taskflow.*` y volver a ejecutar |
| `Database may be already in use` | Otro proceso abrió el archivo sin `AUTO_SERVER=TRUE` | Usar la URL con `;AUTO_SERVER=TRUE` |

---

## Uso de inteligencia artificial

Herramienta utilizada: **Claude (Anthropic)**, mediante su interfaz de chat.

Cómo me ayudó:

- **Orientación de arquitectura:** explicación de conceptos (DAO, WAR, servlets, JDBC) y propuesta de la estructura en capas.
- **Generación de código:** primera versión del backend (modelo, validador, DAO, servlet), del frontend (HTML, CSS y JavaScript) y de las pruebas JUnit.
- **Depuración:** análisis de los registros de error de Maven y Tomcat. Así se identificaron, por ejemplo, el driver de H2 que no se cargaba dentro de Tomcat, el conflicto de credenciales con un archivo de base de datos preexistente y un espacio faltante en una consulta SQL.
- **Revisión de requisitos:** contraste de lo construido con las especificaciones del reto, lo que llevó a corregir el modelo de datos (`status` y `priority` en lugar de un simple booleano).

Todo el código fue ejecutado, probado y depurado localmente antes de incluirlo, y puedo explicar cómo funciona cada parte.

---

## Demostración

Video (máximo 5 minutos): *(agregar enlace)*