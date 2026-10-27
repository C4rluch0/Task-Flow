package org.example.validation;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.example.model.Task;
import org.example.model.TaskPriority;
import org.example.model.TaskStatus;

/**
 * Valida el JSON recibido y lo convierte en un Task.
 * Lanza IllegalArgumentException con un mensaje claro si algo es inválido
 * (el servlet lo convierte en una respuesta HTTP 400).
 *
 * Está separado del servlet para poder probarlo con pruebas unitarias.
 */
public final class TaskValidator {

    public static final int MAX_TITLE = 200;
    public static final int MAX_DESCRIPTION = 1000;

    private TaskValidator() {
    }

    public static Task fromJson(JsonObject json) {
        String title = readString(json, "title");
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("The title is required");
        }
        title = title.trim();
        if (title.length() > MAX_TITLE) {
            throw new IllegalArgumentException("The title cannot exceed " + MAX_TITLE + " characters");
        }

        String description = readString(json, "description");
        if (description != null) {
            description = description.trim();
            if (description.isEmpty()) {
                description = null;
            } else if (description.length() > MAX_DESCRIPTION) {
                throw new IllegalArgumentException(
                        "The description cannot exceed " + MAX_DESCRIPTION + " characters");
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

    /** Devuelve el texto del campo, null si no existe, o error si no es un texto. */
    private static String readString(JsonObject json, String key) {
        if (!json.has(key) || json.get(key).isJsonNull()) {
            return null;
        }
        JsonElement element = json.get(key);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("The field '" + key + "' must be text");
        }
        return element.getAsString();
    }
}