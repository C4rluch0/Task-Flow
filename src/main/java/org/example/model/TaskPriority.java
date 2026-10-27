package org.example.model;

import com.google.gson.annotations.SerializedName;

public enum TaskPriority {
    @SerializedName("Low") LOW("Low"),
    @SerializedName("Medium") MEDIUM("Medium"),
    @SerializedName("High") HIGH("High");

    private final String label;

    TaskPriority(String label) { this.label = label; }

    public String getLabel() { return label; }

    public static TaskPriority fromLabel(String value) {
        if (value != null) {
            for (TaskPriority p : values()) {
                if (p.label.equalsIgnoreCase(value.trim())) {
                    return p;
                }
            }
        }
        throw new IllegalArgumentException(
                "Invalid Priority: '" + value + "'. permitted values: Low, Medium, High");
    }
}