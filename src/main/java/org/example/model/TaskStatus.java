package org.example.model;

import com.google.gson.annotations.SerializedName;

/**
 * Estados permitidos por el reto. El texto de @SerializedName es lo que
 * viaja en el JSON y lo que se guarda en la base de datos.
 */
public enum TaskStatus {
    @SerializedName("Pending") PENDING("Pending"),
    @SerializedName("In Progress") IN_PROGRESS("In Progress"),
    @SerializedName("Completed") COMPLETED("Completed");

    private final String label;

    TaskStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** Convierte un texto ("In Progress") al enum. Lanza error si no es válido. */
    public static TaskStatus fromLabel(String value) {
        if (value != null) {
            for (TaskStatus s : values()) {
                if (s.label.equalsIgnoreCase(value.trim())) {
                    return s;
                }
            }
        }
        throw new IllegalArgumentException(
                "Invalid status: '" + value + "'. Allowed values: Pending, In Progress, Completed");
    }
}