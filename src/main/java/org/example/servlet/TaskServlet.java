package org.example.servlet;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.dao.TaskDao;
import org.example.model.Task;
import org.example.model.TaskStatus;
import org.example.validation.TaskValidator;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;

@WebServlet("/api/tasks/*")
public class TaskServlet extends HttpServlet {

    private final TaskDao dao = new TaskDao();
    private final Gson gson = new Gson();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            Long id = parseId(req);
            if (id == null) {
                sendJson(resp, 200, dao.findAll(parseStatusFilter(req)));
                return;
            }
            Optional<Task> task = dao.findById(id);
            if (task.isPresent()) {
                sendJson(resp, 200, task.get());
            } else {
                sendError(resp, 404, "Tarea no encontrada");
            }
        } catch (IllegalArgumentException e) {
            sendError(resp, 400, e.getMessage());
        } catch (SQLException e) {
            serverError(resp, e);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            Task task = TaskValidator.fromJson(readJsonBody(req));
            sendJson(resp, 201, dao.create(task));
        } catch (IllegalArgumentException e) {
            sendError(resp, 400, e.getMessage());
        } catch (SQLException e) {
            serverError(resp, e);
        }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            Long id = parseId(req);
            if (id == null) {
                sendError(resp, 400, "Falta el ID en la URL");
                return;
            }
            Task task = TaskValidator.fromJson(readJsonBody(req));
            task.setId(id);
            if (dao.update(task)) {
                // Se vuelve a leer para devolver también la fecha de creación
                sendJson(resp, 200, dao.findById(id).orElse(task));
            } else {
                sendError(resp, 404, "Tarea no encontrada");
            }
        } catch (IllegalArgumentException e) {
            sendError(resp, 400, e.getMessage());
        } catch (SQLException e) {
            serverError(resp, e);
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            Long id = parseId(req);
            if (id == null) {
                sendError(resp, 400, "Falta el ID en la URL");
                return;
            }
            if (dao.delete(id)) {
                resp.setStatus(204); // sin contenido
            } else {
                sendError(resp, 404, "Tarea no encontrada");
            }
        } catch (IllegalArgumentException e) {
            sendError(resp, 400, e.getMessage());
        } catch (SQLException e) {
            serverError(resp, e);
        }
    }

    private Long parseId(HttpServletRequest req) {
        String path = req.getPathInfo();
        if (path == null || path.equals("/")) {
            return null;
        }
        try {
            return Long.parseLong(path.substring(1));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("ID inválido");
        }
    }

    private TaskStatus parseStatusFilter(HttpServletRequest req) {
        String status = req.getParameter("status");
        if (status == null || status.isBlank()) {
            return null;
        }
        return TaskStatus.fromLabel(status);
    }

    private JsonObject readJsonBody(HttpServletRequest req) throws IOException {
        req.setCharacterEncoding("UTF-8");
        try {
            JsonElement element = JsonParser.parseReader(req.getReader());
            if (!element.isJsonObject()) {
                throw new IllegalArgumentException("El cuerpo debe ser un objeto JSON");
            }
            return element.getAsJsonObject();
        } catch (JsonParseException e) {
            throw new IllegalArgumentException("JSON inválido");
        }
    }

    private void sendJson(HttpServletResponse resp, int status, Object body) throws IOException {
        resp.setStatus(status);
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        resp.getWriter().write(gson.toJson(body));
    }

    private void sendError(HttpServletResponse resp, int status, String message) throws IOException {
        sendJson(resp, status, Map.of("error", message));
    }

    private void serverError(HttpServletResponse resp, SQLException e) throws IOException {
        log("Error de base de datos", e);
        sendError(resp, 500, "Error interno del servidor");
    }
}