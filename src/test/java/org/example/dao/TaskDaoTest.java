package org.example.dao;

import org.example.db.Database;
import org.example.model.Task;
import org.example.model.TaskPriority;
import org.example.model.TaskStatus;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prueba el DAO contra una base H2 real, pero en un archivo aparte
 * (target/test-db). Nunca toca la base de datos de desarrollo (./data).
 */
class TaskDaoTest {

    // Se ejecuta ANTES de que la clase Database se cargue, para que lea esta ruta.
    // Así es seguro ejecutar la prueba desde Maven o desde el botón de IntelliJ.
    static {
        try {
            Path dir = Path.of("target", "test-db").toAbsolutePath();
            Files.createDirectories(dir);
            System.setProperty("tf.db.path", dir.resolve("taskflow-test").toString());
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private final TaskDao dao = new TaskDao();

    @BeforeAll
    static void createSchema() {
        Database.init();
    }

    @BeforeEach
    void cleanTable() throws SQLException {
        try (Connection con = Database.get(); Statement st = con.createStatement()) {
            st.execute("DELETE FROM tasks");
        }
    }

    private Task newTask(String title, TaskStatus status, TaskPriority priority) {
        Task t = new Task();
        t.setTitle(title);
        t.setDescription("Descripción de " + title);
        t.setStatus(status);
        t.setPriority(priority);
        return t;
    }

    // ---------- CREATE ----------

    @Test
    void createAssignsIdAndCreationDate() throws SQLException {
        Task created = dao.create(newTask("Primera", TaskStatus.PENDING, TaskPriority.MEDIUM));

        assertNotNull(created.getId());
        assertNotNull(created.getCreatedAt());
        assertEquals("Primera", created.getTitle());
        assertEquals(TaskStatus.PENDING, created.getStatus());
        assertEquals(TaskPriority.MEDIUM, created.getPriority());
    }

    @Test
    void createStoresSqlInjectionAttemptAsPlainText() throws SQLException {
        String hostile = "Robert'); DROP TABLE tasks;--";

        Task created = dao.create(newTask(hostile, TaskStatus.PENDING, TaskPriority.LOW));

        // El texto se guardó tal cual y la tabla sigue existiendo
        assertEquals(hostile, dao.findById(created.getId()).orElseThrow().getTitle());
        assertEquals(1, dao.findAll(null).size());
    }

    // ---------- READ ----------

    @Test
    void findAllReturnsNewestFirst() throws SQLException {
        dao.create(newTask("Primera", TaskStatus.PENDING, TaskPriority.LOW));
        dao.create(newTask("Segunda", TaskStatus.PENDING, TaskPriority.LOW));

        List<Task> tasks = dao.findAll(null);

        assertEquals(2, tasks.size());
        assertEquals("Segunda", tasks.get(0).getTitle());
        assertEquals("Primera", tasks.get(1).getTitle());
    }

    @Test
    void findAllFiltersByStatus() throws SQLException {
        dao.create(newTask("A", TaskStatus.PENDING, TaskPriority.LOW));
        dao.create(newTask("B", TaskStatus.COMPLETED, TaskPriority.LOW));
        dao.create(newTask("C", TaskStatus.PENDING, TaskPriority.HIGH));

        List<Task> pending = dao.findAll(TaskStatus.PENDING);

        assertEquals(2, pending.size());
        assertTrue(pending.stream().allMatch(t -> t.getStatus() == TaskStatus.PENDING));
        assertEquals(3, dao.findAll(null).size());
        assertEquals(0, dao.findAll(TaskStatus.IN_PROGRESS).size());
    }

    @Test
    void findByIdReturnsTaskWhenItExists() throws SQLException {
        Task created = dao.create(newTask("Buscar", TaskStatus.IN_PROGRESS, TaskPriority.HIGH));

        Task found = dao.findById(created.getId()).orElseThrow();

        assertEquals("Buscar", found.getTitle());
        assertEquals(TaskStatus.IN_PROGRESS, found.getStatus());
        assertEquals(TaskPriority.HIGH, found.getPriority());
    }

    @Test
    void findByIdReturnsEmptyWhenItDoesNotExist() throws SQLException {
        assertTrue(dao.findById(999_999L).isEmpty());
    }

    // ---------- UPDATE ----------

    @Test
    void updateChangesAllFields() throws SQLException {
        Task created = dao.create(newTask("Original", TaskStatus.PENDING, TaskPriority.LOW));

        created.setTitle("Modificada");
        created.setDescription("Nueva descripción");
        created.setStatus(TaskStatus.COMPLETED);
        created.setPriority(TaskPriority.HIGH);

        assertTrue(dao.update(created));

        Task reloaded = dao.findById(created.getId()).orElseThrow();
        assertEquals("Modificada", reloaded.getTitle());
        assertEquals("Nueva descripción", reloaded.getDescription());
        assertEquals(TaskStatus.COMPLETED, reloaded.getStatus());
        assertEquals(TaskPriority.HIGH, reloaded.getPriority());
    }

    @Test
    void updateReturnsFalseWhenTaskDoesNotExist() throws SQLException {
        Task ghost = newTask("Fantasma", TaskStatus.PENDING, TaskPriority.LOW);
        ghost.setId(999_999L);

        assertFalse(dao.update(ghost));
    }

    // ---------- DELETE ----------

    @Test
    void deleteRemovesTheTask() throws SQLException {
        Task created = dao.create(newTask("Borrar", TaskStatus.PENDING, TaskPriority.LOW));

        assertTrue(dao.delete(created.getId()));
        assertTrue(dao.findById(created.getId()).isEmpty());
    }

    @Test
    void deleteReturnsFalseWhenTaskDoesNotExist() throws SQLException {
        assertFalse(dao.delete(999_999L));
    }

    // ---------- Restricciones de la base de datos ----------

    @Test
    void databaseRejectsInvalidStatusEvenIfValidationIsBypassed() {
        assertThrows(SQLException.class, () -> {
            try (Connection con = Database.get(); Statement st = con.createStatement()) {
                st.execute("INSERT INTO tasks (title, status) VALUES ('x', 'Bogus')");
            }
        });
    }
}