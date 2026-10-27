package org.example.validation;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.example.model.Task;
import org.example.model.TaskPriority;
import org.example.model.TaskStatus;

public final class TaskValidator {

    public static final int MAX_TITLE = 200;
    public static final int MAX_DESCRIPTION = 1000;

    private TaskValidator() {
    }

    public static Task fromJson(JsonObject json) {
        String title = readString(json, "title");
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("El título es obligatorio");
        }
        title = title.trim();
        if (title.length() > MAX_TITLE) {
            throw new IllegalArgumentException("El título no puede superar " + MAX_TITLE + " caracteres");
        }

        String description = readString(json, "description");
        if (description != null) {
            description = description.trim();
            if (description.isEmpty()) {
                description = null;
            } else if (description.length() > MAX_DESCRIPTION) {
                throw new IllegalArgumentException(
                        "La descripción no puede superar " + MAX_DESCRIPTION + " caracteres");
            }
        }

        // Si no se envían, se usan valores por defecto; si se envían mal, se rechazan.
        String status = readString(json, "status");
        String priority = readString(json, "priority");

        Task task = new Task();
        task.setTitle(title);
        task.setDescription(description);
        task.setStatus(status == null ? TaskStatus.PENDING : TaskStatus.fromLabel(status));
        task.setPriority(priority == null ? TaskPriority.MEDIUM : TaskPriority.fromLabel(priority));
        return task;
    }

    private static String readString(JsonObject json, String key) {
        if (!json.has(key) || json.get(key).isJsonNull()) {
            return null;
        }
        JsonElement element = json.get(key);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("El campo '" + key + "' debe ser un texto");
        }
        return element.getAsString();
    }
}