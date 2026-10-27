package org.example.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TaskEnumsTest {

    @Test
    void statusFromLabelAcceptsEveryLabel() {
        for (TaskStatus s : TaskStatus.values()) {
            assertEquals(s, TaskStatus.fromLabel(s.getLabel()));
        }
    }

    @Test
    void statusFromLabelIgnoresCaseAndSurroundingSpaces() {
        assertEquals(TaskStatus.IN_PROGRESS, TaskStatus.fromLabel("  in progress "));
    }

    @Test
    void statusFromLabelRejectsUnknownAndNull() {
        assertThrows(IllegalArgumentException.class, () -> TaskStatus.fromLabel("Done"));
        assertThrows(IllegalArgumentException.class, () -> TaskStatus.fromLabel(null));
    }

    @Test
    void priorityFromLabelAcceptsEveryLabel() {
        for (TaskPriority p : TaskPriority.values()) {
            assertEquals(p, TaskPriority.fromLabel(p.getLabel()));
        }
    }

    @Test
    void priorityFromLabelRejectsUnknownAndNull() {
        assertThrows(IllegalArgumentException.class, () -> TaskPriority.fromLabel("Urgent"));
        assertThrows(IllegalArgumentException.class, () -> TaskPriority.fromLabel(null));
    }
}