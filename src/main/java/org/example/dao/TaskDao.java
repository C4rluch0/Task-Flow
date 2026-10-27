package org.example.dao;

import org.example.db.Database;
import org.example.model.Task;
import org.example.model.TaskPriority;
import org.example.model.TaskStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Acceso a datos: todo el SQL de la tabla "tasks" vive aquí.
 * Siempre se usa PreparedStatement para evitar inyección SQL.
 */
public class TaskDao {

    private static final String COLUMNS =
            "id, title, description, status, priority, created_at";

    // READ: todas las tareas; si status != null, filtra por estado
    public List<Task> findAll(TaskStatus status) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM tasks"
                + (status != null ? " WHERE status = ?" : "")
                + " ORDER BY id DESC";
        List<Task> tasks = new ArrayList<>();

        try (Connection con = Database.get();
             PreparedStatement ps = con.prepareStatement(sql)) {
            if (status != null) {
                ps.setString(1, status.getLabel());
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    tasks.add(mapRow(rs));
                }
            }
        }
        return tasks;
    }

    // READ: una tarea por id
    public Optional<Task> findById(long id) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM tasks WHERE id = ?";

        try (Connection con = Database.get();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }
        return Optional.empty();
    }

    // CREATE: inserta y devuelve la tarea completa (con id y fecha de creación)
    public Task create(Task task) throws SQLException {
        String sql = "INSERT INTO tasks (title, description, status, priority) VALUES (?, ?, ?, ?)";
        long newId;

        try (Connection con = Database.get();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, task.getTitle());
            ps.setString(2, task.getDescription());
            ps.setString(3, task.getStatus().getLabel());
            ps.setString(4, task.getPriority().getLabel());
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("No generated ID was returned");
                }
                newId = keys.getLong(1);
            }
        }
        return findById(newId).orElseThrow(() -> new SQLException("The created task could not be found"));
    }

    // UPDATE: devuelve true si encontró y modificó la tarea
    public boolean update(Task task) throws SQLException {
        String sql = "UPDATE tasks SET title = ?, description = ?, status = ?, priority = ? WHERE id = ?";

        try (Connection con = Database.get();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, task.getTitle());
            ps.setString(2, task.getDescription());
            ps.setString(3, task.getStatus().getLabel());
            ps.setString(4, task.getPriority().getLabel());
            ps.setLong(5, task.getId());
            return ps.executeUpdate() > 0;
        }
    }

    // DELETE: devuelve true si existía y se eliminó
    public boolean delete(long id) throws SQLException {
        String sql = "DELETE FROM tasks WHERE id = ?";

        try (Connection con = Database.get();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    // Convierte una fila del ResultSet en un objeto Task
    private Task mapRow(ResultSet rs) throws SQLException {
        Task t = new Task();
        t.setId(rs.getLong("id"));
        t.setTitle(rs.getString("title"));
        t.setDescription(rs.getString("description"));
        t.setStatus(TaskStatus.fromLabel(rs.getString("status")));
        t.setPriority(TaskPriority.fromLabel(rs.getString("priority")));
        Timestamp created = rs.getTimestamp("created_at");
        t.setCreatedAt(created != null ? created.toLocalDateTime().toString() : null);
        return t;
    }
}