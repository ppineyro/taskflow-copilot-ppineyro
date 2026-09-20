package com.taskflow.unit;

import com.taskflow.exception.TaskStateException;
import com.taskflow.model.Task;
import com.taskflow.model.TaskStatus;
import com.taskflow.model.Priority;
import com.taskflow.repository.TaskRepository;
import com.taskflow.service.TaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

public class ReasignarTareaServiceTest {

    @Mock
    private TaskRepository repository;

    @InjectMocks
    private TaskService taskService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void reasignar_tarea_todo_sin_responsable_guarda_con_nuevo_assignee() throws Exception {
        Task tarea = new Task(5L, "Tarea5", "d", TaskStatus.TODO, Priority.MED, 1L, null, LocalDate.now().plusDays(1));
        when(repository.findById(5L)).thenReturn(Optional.of(tarea));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Task encontrada = taskService.buscarPorId(5L).orElseThrow();
        Task result = taskService.reasignar(encontrada, 2L);

        ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
        verify(repository).save(captor.capture());
        Task saved = captor.getValue();

        assertThat(saved.getAssigneeId()).isEqualTo(2L);
        assertThat(result.getAssigneeId()).isEqualTo(2L);
    }

    @Test
    void reasignar_tarea_done_lanza_TaskStateException_y_no_guarda() throws Exception {
        Task tarea = new Task(6L, "Tarea6", "d2", TaskStatus.DONE, Priority.HIGH, 1L, 1L, LocalDate.now().minusDays(1));
        when(repository.findById(6L)).thenReturn(Optional.of(tarea));

        Task encontrada = taskService.buscarPorId(6L).orElseThrow();
        assertThrows(TaskStateException.class, () -> taskService.reasignar(encontrada, 3L));
        verify(repository, never()).save(any());
    }
}
