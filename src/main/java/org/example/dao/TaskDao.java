package org.example.dao;

import org.example.db.Database;
import org.example.model.Task;
import org.example.model.TaskPriority;
import org.example.model. TaskStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class TaskDao {

    private static final String COLUMS =
            "id, title, description, status, priority, created_at";

    public List<Task> findAll(TaskStatus status) throws SQLException {
        String sql = "SELECT " + COLUMS + " FROM tasks" + (status != null ? " WHERE status = ?" : "") + " ORDER BY id DESC";
        List<Task> tasks = new ArrayList<>();

        try (Connection con = Database.get(); PreparedStatement ps = con.prepareStatement(sql)) {
            if (status != null){
                ps.setString(1,status.getLabel());
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    tasks.add(mapRow(rs));
                }
            }
        }
        return tasks;
    }

    public Optional<Task> findById(long id) throws SQLException {
        String sql = "SELECT " + COLUMS + " FROM tasks WHERE id = ?";

        try (Connection con = Database.get(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()){
                if (rs.next()){
                    return Optional.of(mapRow(rs));
                }
            }
        }
        return Optional.empty();
    }

    public Task create(Task task) throws SQLException {
        String sql = "INSERT INTO tasks (title, description, status, priority) VALUES (?, ?, ?, ?)";
        long newId;

        try (Connection con = Database.get(); PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, task.getTitle());
            ps.setString(2, task.getDescription());
            ps.setString(3, task.getStatus().getLabel());
            ps.setString(4, task.getPriority().getLabel());
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("The denerated ID was not obtained");
                }
                newId = keys.getLong(1);
            }
        }
        return findById(newId).orElseThrow(() -> new SQLException("The created task was not found"));
    }

    public boolean update(Task task) throws SQLException {
        String sql = "UPDATE tasks SET title = ?, description = ?, status = ?, priority = ? WHERE id = ?";

        try (Connection con = Database.get(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1,task.getTitle());
            ps.setString(2, task.getDescription());
            ps.setString(3, task.getStatus().getLabel());
            ps.setString(4, task.getPriority().getLabel());
            ps.setLong(5, task.getId());
            return ps.executeUpdate() > 0;
        }
    }

    public boolean delete(long id) throws SQLException {
        String sql = "DELETE FROM tasks WHERE id = ?";

        try (Connection con = Database.get(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    private Task mapRow(ResultSet rs)  throws SQLException {
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