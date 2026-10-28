# TaskFlow

Aplicación web para administrar las tareas de un equipo (MVP del *Technical Challenge — Web Full Stack Developer*).
Permite **listar, crear, editar, eliminar, cambiar el estado y filtrar tareas por estado**. Incluye una API REST, persistencia con H2, pruebas JUnit y ejecución local o mediante Docker.

| Capa | Tecnología |
|---|---|
| Frontend | HTML, CSS y JavaScript puro (sin frameworks) |
| Backend | Java 21+, Jakarta Servlet 6.1, JDBC, Gson |
| Servidor | Apache Tomcat 11 (lo descarga y arranca Maven automáticamente) |
| Base de datos | H2 en modo archivo (persistente) |
| Pruebas | JUnit 5 (39 métodos `@Test` en 4 clases) |
| Construcción | Maven |
| Contenedores | Docker / Docker Compose |

---

## Requisitos

Puedes elegir **una de estas dos modalidades**:

- **Ejecución local:** JDK 21 o superior, Maven 3.9+ y acceso a Internet la primera vez para descargar dependencias y Tomcat.
- **Ejecución con Docker Compose desde el código:** Docker Desktop o Docker Engine con el plugin **Docker Compose**. No hace falta instalar Java, Maven, Tomcat ni H2 en el equipo anfitrión.
- **Ejecución desde Docker Hub (sin código fuente):** Docker Desktop o Docker Engine. No hace falta Git, Maven ni Docker Compose: basta con descargar y ejecutar la imagen pública.

Comprobar las herramientas locales (solo si usarás Maven):

```bash
java -version
mvn -version
```

## Inicio rápido con Docker

1. Clona el repositorio o descarga su ZIP desde GitHub y extráelo.
2. Abre una terminal en la carpeta que contiene `docker-compose.yml` y `Dockerfile`.
3. Ejecuta:

```bash
docker compose up --build -d
```

4. Abre **http://localhost:8080/TaskFlow/**.

Docker construye el WAR en una etapa con Maven y Java 21 y lo ejecuta en una imagen de Tomcat 11 con JRE 21. La primera construcción descargará las imágenes y dependencias necesarias. Las pruebas se **omiten** en la construcción de la imagen (`-DskipTests`); para ejecutarlas usa Maven o GitHub Actions cuando esté configurado.

Comandos de administración:

```bash
docker compose ps            # Estado del contenedor
docker compose logs -f       # Registros de Tomcat y de la aplicación
docker compose down          # Detener y eliminar el contenedor (conserva el volumen)
docker compose up --build -d # Reconstruir después de cambiar el código
```

El archivo `docker-compose.yml` publica el puerto **8080:8080** y monta el volumen `taskflow-data` en `/data`. La URL H2 del contenedor apunta a `/data/taskflow` por medio de `TF_DB_PATH`. Por eso, los datos sobreviven a `docker compose down` y a la recreación del contenedor, siempre que mantengas el volumen.

> **Atención:** `docker compose down -v` **también borra el volumen** y sus datos. No lo ejecutes si deseas conservar las tareas.

Si el puerto 8080 está ocupado, cambia `"8080:8080"` por, por ejemplo, `"8081:8080"` en `docker-compose.yml` y abre `http://localhost:8081/TaskFlow/`.

Alternativa sin Compose:

```bash
docker build -t taskflow .
docker run -d --name taskflow -p 8080:8080 -v taskflow-data:/data taskflow
```

## Ejecutar TaskFlow desde Docker Hub 

La imagen de TaskFlow **ya está publicada** en el repositorio público de Docker Hub:

- **Repositorio:** https://hub.docker.com/r/c4rluch0/taskflow
- **Imagen:** `c4rluch0/taskflow:1.0`
- **Plataforma publicada:** `linux/amd64`

Esta modalidad está pensada para compañeros o evaluadores que **solo quieren utilizar la aplicación**, sin descargar el código fuente ni instalar Java, Maven o Tomcat. Únicamente necesitan **Docker Desktop / Docker Engine funcionando** y acceso a Internet para obtener la imagen la primera vez.

### Descargar y ejecutar con un solo comando

Abre una terminal (PowerShell, CMD o terminal de Linux/macOS) y ejecuta:

```bash
docker run -d --name taskflow -p 127.0.0.1:8080:8080 -v taskflow-data:/data --restart unless-stopped c4rluch0/taskflow:1.0
```

Si la imagen no existe en el equipo, Docker la descargará de Docker Hub automáticamente (`docker pull` no es obligatorio). Después creará el contenedor `taskflow`, iniciará Tomcat y montará el volumen `taskflow-data` en `/data` para conservar la base H2. El puerto se publica solo en `127.0.0.1` para limitar el acceso a la computadora local.

**Abre:** http://localhost:8080/TaskFlow/

**Consola H2 (si el servlet se registra correctamente):** http://localhost:8080/TaskFlow/h2-console/ — consulta la sección [Consola web H2](#consola-web-h2), porque esa ruta puede devolver 404 en la versión actual.

Para descargar la imagen por separado, si lo prefieres:

```bash
docker pull c4rluch0/taskflow:1.0
```

### Administrar el contenedor descargado

```bash
docker ps                      # Ver si TaskFlow está en ejecución
docker logs -f taskflow         # Revisar mensajes de Tomcat y errores
docker stop taskflow           # Detener el contenedor
docker start taskflow          # Volver a iniciarlo
docker rm -f taskflow          # Eliminar el contenedor, sin borrar el volumen
```

El volumen `taskflow-data` **no se elimina** al usar `docker rm -f taskflow`. No borres ese volumen si necesitas conservar las tareas. Si ya existe un contenedor llamado `taskflow`, debes iniciarlo con `docker start taskflow` o eliminarlo antes de volver a ejecutar `docker run`.

Si el puerto 8080 está ocupado, reemplaza `127.0.0.1:8080:8080` por `127.0.0.1:8081:8080` y abre `http://localhost:8081/TaskFlow/`.

> **Compatibilidad:** la imagen publicada es `linux/amd64`. En equipos ARM (por ejemplo, algunos Mac con Apple Silicon) puede requerirse emulación de arquitectura; el rendimiento o la compatibilidad pueden variar. La publicación en Docker Hub confirma que la imagen está disponible, pero conviene validar el arranque real de la aplicación en otro equipo.



---

## Ejecución local con Maven

Desde la raíz del proyecto:

```bash
mvn clean package cargo:run
```

Esto compila el WAR, pasa por la fase de pruebas de Maven y arranca Tomcat 11 mediante Cargo. **Comprueba que Maven detecte las pruebas JUnit 5** (consulta la advertencia en la sección de pruebas). Cuando el servidor inicie, abre **http://localhost:8080/TaskFlow/**. Para detenerlo, pulsa `Ctrl+C`.

Para empaquetar y arrancar **sin ejecutar pruebas**:

```bash
mvn clean package cargo:run -DskipTests
```

La base se inicializa al arrancar (`AppInitializer` invoca `Database.init()`), crea la tabla `tasks` si no existe y usa la ruta `data/taskflow` definida por el plugin Cargo. Por eso los datos locales se conservan al ejecutar `mvn clean`.

## Consola web H2

El proyecto contiene `H2ConsoleSetup.java` y `AppInitializer.java`: al iniciar TaskFlow se **intenta** registrar el servlet de la consola H2 con la ruta:

**http://localhost:8080/TaskFlow/h2-console/**

La funcionalidad se encuentra **habilitada por defecto** salvo que se establezca `TF_H2_CONSOLE=false` o la propiedad `-Dtf.h2.console=false`. Si el registro funciona, la consola precarga el driver, la URL y el usuario, y el usuario podrá conectarse utilizando la contraseña configurada.

| Parámetro | En local (Maven + Cargo) | En Docker Compose |
|---|---|---|
| Driver JDBC | `org.h2.Driver` | `org.h2.Driver` |
| URL JDBC | `jdbc:h2:<ruta-absoluta>/data/taskflow;AUTO_SERVER=TRUE` | `jdbc:h2:/data/taskflow;AUTO_SERVER=TRUE` |
| Usuario por defecto | `sa` | `sa` |
| Contraseña por defecto | *(vacía)* | *(vacía)* |

---

## Pruebas automatizadas

JUnit 5 cubre **39 métodos de prueba** distribuidos en cuatro clases:

| Clase de prueba | Cantidad | Qué comprueba |
|---|---:|---|
| `TaskValidatorTest` | 15 | Campos obligatorios, límites, valores por defecto y validaciones |
| `TaskEnumsTest` | 5 | Conversión y rechazo de estados y prioridades |
| `TaskDaoTest` | 11 | Operaciones CRUD, filtros, ordenación, consultas preparadas y restricciones H2 |
| `H2ConsoleSetupTest` | 8 | Activación de la consola, archivo de conexiones, rutas con acentos y registro simulado del servlet |
| **Total** | **39** | |

Para ejecutar todas las pruebas, con Java 21 y Maven instalados:

```bash
mvn clean test
```

Para ejecutar solamente las pruebas de H2:

```bash
mvn -Dtest=H2ConsoleSetupTest test
```

Para compilar, probar y verificar el paquete:

```bash
mvn clean verify
```

Los informes quedan en `target/surefire-reports/`. Las pruebas `TaskDaoTest` utilizan una base independiente bajo `target/test-db` y no deberían modificar los datos de `data/`.

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

**Errores.** Siempre devuelven JSON con la forma `{"error": "mensaje"}`. La interfaz y los mensajes de la API están en inglés:

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
├── Dockerfile                     ← imagen del contenedor (construcción en dos etapas)
├── docker-compose.yml             ← arranque con un solo comando y volumen para los datos
├── .dockerignore                  ← reduce el contexto de construcción Docker
├── .gitignore                     ← excluye archivos generados y bases locales
├── data/                          ← base de datos H2 local (se crea sola, no se versiona)
└── src/
    ├── main/
    │   ├── java/org/example/
    │   │   ├── model/             Task, TaskStatus, TaskPriority
    │   │   ├── validation/        TaskValidator (reglas de validación)
    │   │   ├── dao/               TaskDao (todo el SQL)
    │   │   ├── db/                Database, AppInitializer, H2ConsoleSetup
    │   │   └── servlet/           TaskServlet (API REST)
    │   └── webapp/
    │       ├── index.html         Estructura de la página
    │       ├── css/style.css      Estilos
    │       └── js/app.js          Lógica del cliente (llamadas a la API y manejo de la interfaz)
    └── test/java/org/example/
        ├── dao/                   TaskDaoTest
        ├── model/                 TaskEnumsTest
        ├── validation/            TaskValidatorTest
        └── h2/                    H2ConsoleSetupTest
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
- **Ruta H2 configurable.** `Database.java` prioriza la propiedad Java `tf.db.path`, después la variable de entorno `TF_DB_PATH` y finalmente `./data/taskflow`. El `pom.xml` configura la ruta local bajo `data/taskflow`, fuera de `target/`, para que sobreviva a `mvn clean`.
- **Carga explícita del driver (`Class.forName("org.h2.Driver")`).** Dentro de Tomcat, el driver vive en `WEB-INF/lib` y `DriverManager` no lo detecta por sí solo; sin esto la aplicación fallaba al arrancar con `No suitable driver`.
- **Docker en dos etapas.** La primera (Maven + JDK 21) compila el WAR **omitiendo las pruebas**; la segunda (Tomcat 11 + JRE 21) contiene el WAR y su entorno de ejecución. `TF_DB_PATH=/data/taskflow` y el volumen de Compose permiten conservar la base de datos entre recreaciones del contenedor.
- **Cargo para ejecutar con un solo comando.** El evaluador no necesita instalar ni configurar Tomcat.
- **Frontend sin frameworks, dividido en HTML, CSS y JavaScript**, con rutas relativas (`api/tasks`). Funciona sin importar el nombre del servidor y desde otro dispositivo de la red, y no depende de internet (tipografía del sistema, sin librerías externas).
- **`textContent` en lugar de `innerHTML`** al mostrar datos del usuario, para impedir ataques XSS.
- **Se recarga la lista desde el servidor después de cada cambio**, en lugar de modificar la pantalla a mano, de modo que lo mostrado siempre coincide con lo guardado.
- **Interfaz de aplicación, sobria y responsive.** Barra superior de marca, lista tipo tabla (tarea, prioridad, estado, fecha y acciones) que en móvil se apila fila por fila, y el formulario en una ventana emergente (`<dialog>` nativo) que sirve para crear y para editar. Toda la paleta vive en variables CSS, lo que permitió añadir un **modo oscuro automático** (según la preferencia del sistema) sin duplicar estilos. Se verificó por cálculo que los pares de colores de texto cumplen el contraste mínimo de WCAG (4,5:1) en ambos modos.
- **El color comunica información, nunca sola.** La prioridad se indica con un punto de color más su texto, y el estado con una etiqueta de color que a la vez es el selector para cambiarlo.
- **Retroalimentación al usuario.** Validación del título junto al propio campo, contadores de caracteres, avisos breves al crear, editar o eliminar, y mensajes de error que explican qué falló (dentro de la ventana si está abierta). La confirmación de borrado se hace dentro de la fila, con el foco en la opción segura (Cancelar), en lugar del cuadro del navegador. Hay un estado vacío con invitación a crear la primera tarea.
- **Accesibilidad básica:** el `<dialog>` nativo gestiona el foco y se cierra con `Escape`; foco visible para teclado; botones de icono con nombre accesible; atributos ARIA en filtros y errores; y se respeta la preferencia de movimiento reducido.
- **La ventana del formulario conserva lo escrito si el servidor rechaza los datos**, para que se pueda corregir sin volver a teclear, y deshabilita el botón mientras guarda para evitar envíos dobles. Un clic en el fondo oscuro solo la cierra si el clic empezó y terminó allí, para no perder el texto al soltar el ratón fuera después de seleccionar.
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
- [x] Pruebas JUnit implementadas (39 métodos, incluidas 8 pruebas de configuración de H2)

### Pendientes / no incluidas

- [ ] Confirmar que la consola web H2 queda registrada en un despliegue real de Tomcat (las pruebas actuales simulan el contexto)
- [ ] Agregar al repositorio el workflow YAML de GitHub Actions (no está incluido en el ZIP)
- [ ] Fijar una versión moderna de Surefire para mayor reproducibilidad de las pruebas en otros equipos
- [ ] Búsqueda de tareas por título *(opcional)*
- [ ] Indicadores o estadísticas de tareas *(opcional)*
- [ ] Pruebas automatizadas de los servlets y del frontend (hoy se cubren indirectamente con las pruebas del validador y el DAO, y manualmente)
- [ ] Paginación del listado
- [ ] Ejecutar el contenedor con un usuario sin privilegios (hoy usa el usuario por defecto de la imagen)
- [ ] Autenticación (el reto no la exige)
- [ ] Credenciales de base de datos seguras para producción (hoy usa `sa` sin contraseña, solo apto para desarrollo local)

---

## Uso de inteligencia artificial

Herramienta utilizada: **Claude (Anthropic)**, mediante su interfaz de chat.

Cómo me ayudó:

- **Orientación de arquitectura:** explicación de conceptos (DAO, WAR, servlets, JDBC) y propuesta de la estructura en capas.
- **Generación de código:** primera versión del backend (modelo, validador, DAO, servlet), del frontend (HTML, CSS y JavaScript) y de las pruebas JUnit.
- **Depuración:** análisis de los registros de error de Maven y Tomcat. Así se identificaron, por ejemplo, el driver de H2 que no se cargaba dentro de Tomcat, el conflicto de credenciales con un archivo de base de datos preexistente y un espacio faltante en una consulta SQL.
- **Revisión de requisitos:** contraste de lo construido con las especificaciones del reto, lo que llevó a corregir el modelo de datos (`status` y `priority` en lugar de un simple booleano).

Esta sección describe el proceso original documentado por el autor. Los resultados de las pruebas y el funcionamiento de la consola H2 deben verificarse en cada entorno; la presencia de pruebas unitarias no garantiza el despliegue en Tomcat.

---

## Demostración

Video (máximo 5 minutos): *(agregar enlace)*