package org.example.model;

import com.google.gson.annotations.SerializedName;

public enum TaskStatus {
    @SerializedName("Pending") PENDING("Pending"),
    @SerializedName("In Progress") IN_PROGRESS("In Progress"),
    @SerializedName("Completed") COMPLETED("Completed");

    private final String label;

    TaskStatus(String label) { this.label = label; }

    public String getLabel() { return label; }

    public static TaskStatus fromLabel(String value) {
        if (value != null) {
            for (TaskStatus s : values()) {
                if (s.label.equalsIgnoreCase(value.trim())){
                    return s;
                }
            }
        }
        throw new IllegalArgumentException("Invalid state '" + value + "'. permitted values: Pending, In Progress Completed.");
    }
}
