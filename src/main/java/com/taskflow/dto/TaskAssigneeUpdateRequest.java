package com.taskflow.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * TaskAssigneeUpdateRequest — cuerpo de PATCH /tasks/{id}/assignee: solo el id del responsable.
 */
public record TaskAssigneeUpdateRequest(

        @NotNull(message = "El assigneeId es obligatorio.")
        @Positive(message = "El assigneeId debe ser un número positivo.")
        Long assigneeId
) {
}
