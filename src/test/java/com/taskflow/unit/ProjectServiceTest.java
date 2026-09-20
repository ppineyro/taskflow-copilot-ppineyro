package com.taskflow.unit;

import com.taskflow.exception.TaskValidationException;
import com.taskflow.model.Priority;
import com.taskflow.model.Project;
import com.taskflow.model.Task;
import com.taskflow.model.TaskStatus;
import com.taskflow.repository.ProjectRepository;
import com.taskflow.repository.TaskRepository;
import com.taskflow.repository.UserRepository;
import com.taskflow.service.ProjectService;
import com.taskflow.service.ProjectSummary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ProjectService service;

    @Test
    void resumen_conTareas_calculaConteosYVencidas() throws Exception {
        Project proyecto = new Project(2L, "App Móvil", "d", 2L, null);
        Task t1 = tarea(3L, "Tarea 3", TaskStatus.TODO, Priority.MED, null, null);
        Task t2 = tarea(4L, "Tarea 4", TaskStatus.IN_PROGRESS, Priority.HIGH, null, LocalDate.now().plusDays(5));
        Task t3 = tarea(5L, "Tarea 5", TaskStatus.IN_PROGRESS, Priority.LOW, null, LocalDate.now().minusDays(1)); // vencida
        Task t4 = tarea(6L, "Tarea 6", TaskStatus.DONE, Priority.MED, null, LocalDate.now().minusDays(2));

        when(taskRepository.findByProjectId(2L)).thenReturn(List.of(t1, t2, t3, t4));

        ProjectSummary summary = service.resumen(proyecto);

        assertEquals(4, summary.totalTasks());
        assertThat(summary.byStatus()).containsEntry(TaskStatus.TODO, 1);
        assertThat(summary.byStatus()).containsEntry(TaskStatus.IN_PROGRESS, 2);
        assertThat(summary.byStatus()).containsEntry(TaskStatus.DONE, 1);
        assertEquals(1, summary.overdue());
    }

    @Test
    void resumen_proyectoSinTareas_devuelveCeros() {
        Project proyecto = new Project(3L, "Empty", "d", 1L, null);
        when(taskRepository.findByProjectId(3L)).thenReturn(List.of());

        ProjectSummary summary = service.resumen(proyecto);

        assertEquals(0, summary.totalTasks());
        assertThat(summary.byStatus()).containsEntry(TaskStatus.TODO, 0);
        assertThat(summary.byStatus()).containsEntry(TaskStatus.IN_PROGRESS, 0);
        assertThat(summary.byStatus()).containsEntry(TaskStatus.DONE, 0);
        assertEquals(0, summary.overdue());
    }

    private Task tarea(Long id, String title, TaskStatus status, Priority priority, Long assigneeId, LocalDate dueDate) {
        try {
            return new Task(id, title, "desc", status, priority, 2L, assigneeId, dueDate);
        } catch (TaskValidationException e) {
            throw new IllegalStateException("dato de prueba inválido", e);
        }
    }
}
