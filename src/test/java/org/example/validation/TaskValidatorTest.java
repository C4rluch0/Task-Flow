package org.example.validation;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.example.model.Task;
import org.example.model.TaskPriority;
import org.example.model.TaskStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskValidatorTest {

    private static JsonObject json(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    /** Ejecuta la validación esperando un error y devuelve su mensaje. */
    private static String errorOf(String jsonText) {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> TaskValidator.fromJson(json(jsonText)));
        return ex.getMessage();
    }

    // ---------- Casos válidos ----------

    @Test
    void acceptsTaskWithAllFields() {
        Task t = TaskValidator.fromJson(json("""
                {"title":"Study","description":"Java","status":"In Progress","priority":"High"}
                """));

        assertEquals("Study", t.getTitle());
        assertEquals("Java", t.getDescription());
        assertEquals(TaskStatus.IN_PROGRESS, t.getStatus());
        assertEquals(TaskPriority.HIGH, t.getPriority());
    }

    @Test
    void usesDefaultsWhenStatusAndPriorityAreMissing() {
        Task t = TaskValidator.fromJson(json("{\"title\":\"Title only\"}"));

        assertEquals(TaskStatus.PENDING, t.getStatus());
        assertEquals(TaskPriority.MEDIUM, t.getPriority());
        assertNull(t.getDescription());
    }

    @Test
    void trimsTitle() {
        Task t = TaskValidator.fromJson(json("{\"title\":\"   Hello   \"}"));
        assertEquals("Hello", t.getTitle());
    }

    @Test
    void blankDescriptionBecomesNull() {
        Task t = TaskValidator.fromJson(json("{\"title\":\"x\",\"description\":\"   \"}"));
        assertNull(t.getDescription());
    }

    @Test
    void statusAndPriorityIgnoreCase() {
        Task t = TaskValidator.fromJson(json(
                "{\"title\":\"x\",\"status\":\"in progress\",\"priority\":\"high\"}"));

        assertEquals(TaskStatus.IN_PROGRESS, t.getStatus());
        assertEquals(TaskPriority.HIGH, t.getPriority());
    }

    @Test
    void acceptsTitleOfExactlyMaxLength() {
        String title = "a".repeat(TaskValidator.MAX_TITLE);
        Task t = TaskValidator.fromJson(json("{\"title\":\"" + title + "\"}"));
        assertEquals(TaskValidator.MAX_TITLE, t.getTitle().length());
    }

    @Test
    void acceptsDescriptionOfExactlyMaxLength() {
        String description = "a".repeat(TaskValidator.MAX_DESCRIPTION);
        Task t = TaskValidator.fromJson(json(
                "{\"title\":\"x\",\"description\":\"" + description + "\"}"));
        assertEquals(TaskValidator.MAX_DESCRIPTION, t.getDescription().length());
    }

    // ---------- Casos inválidos ----------

    @Test
    void rejectsMissingTitle() {
        assertTrue(errorOf("{}").contains("required"));
    }

    @Test
    void rejectsNullTitle() {
        assertTrue(errorOf("{\"title\":null}").contains("required"));
    }

    @Test
    void rejectsBlankTitle() {
        assertTrue(errorOf("{\"title\":\"    \"}").contains("required"));
    }

    @Test
    void rejectsTitleLongerThanMax() {
        String title = "a".repeat(TaskValidator.MAX_TITLE + 1);
        assertTrue(errorOf("{\"title\":\"" + title + "\"}").contains("200"));
    }

    @Test
    void rejectsDescriptionLongerThanMax() {
        String description = "a".repeat(TaskValidator.MAX_DESCRIPTION + 1);
        assertTrue(errorOf("{\"title\":\"x\",\"description\":\"" + description + "\"}").contains("1000"));
    }

    @Test
    void rejectsTitleThatIsNotText() {
        assertTrue(errorOf("{\"title\":123}").contains("text"));
    }

    @Test
    void rejectsUnknownStatus() {
        String message = errorOf("{\"title\":\"x\",\"status\":\"Done\"}");
        assertTrue(message.contains("Pending"), "The message must list the allowed values");
    }

    @Test
    void rejectsUnknownPriority() {
        String message = errorOf("{\"title\":\"x\",\"priority\":\"Urgent\"}");
        assertTrue(message.contains("Low, Medium, High"), "The message must list the allowed values");
    }
}