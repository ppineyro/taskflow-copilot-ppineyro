package com.taskflow.service;

import com.taskflow.model.TaskStatus;

import java.util.Map;

/**
 * Valor de servicio que contiene los conteos del resumen de proyecto.
 */
public record ProjectSummary(int totalTasks, Map<TaskStatus, Integer> byStatus, int overdue) {
}
