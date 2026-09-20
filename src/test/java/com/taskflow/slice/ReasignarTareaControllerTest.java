package com.taskflow.slice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskflow.controller.TaskController;
import com.taskflow.dto.TaskAssigneeUpdateRequest;
import com.taskflow.exception.TaskNotFoundException;
import com.taskflow.exception.TaskStateException;
import com.taskflow.model.Task;
import com.taskflow.model.TaskStatus;
import com.taskflow.model.Priority;
import com.taskflow.security.JwtAuthenticationFilter;
import com.taskflow.service.ProjectService;
import com.taskflow.service.TaskService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TaskController.class)
@AutoConfigureMockMvc(addFilters = false)
public class ReasignarTareaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TaskService taskService;

    @MockBean
    private ProjectService projectService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void patch_assignee_devuelve_200_y_assigneeId_actualizado() throws Exception {
        Task tarea = new Task(4L, "Tarea4", "d4", TaskStatus.TODO, Priority.LOW, 1L, null, LocalDate.now().plusDays(2));
        when(taskService.buscarPorId(4L)).thenReturn(Optional.of(tarea));
        when(taskService.reasignar(eq(tarea), eq(2L))).thenAnswer(invocation -> {
            Task t = invocation.getArgument(0);
            t.setAssigneeId(2L);
            return t;
        });

        TaskAssigneeUpdateRequest req = new TaskAssigneeUpdateRequest(2L);
        mockMvc.perform(patch("/tasks/4/assignee")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assigneeId").value(2));
    }

    @Test
    void patch_assignee_422_se_taskService_lanza_TaskStateException() throws Exception {
        when(taskService.buscarPorId(2L)).thenReturn(Optional.of(new Task(2L, "Tarea2", "d2", TaskStatus.DONE, Priority.HIGH, 1L, 1L, LocalDate.now().minusDays(1))));
        when(taskService.reasignar(any(Task.class), anyLong())).thenThrow(new TaskStateException("No se puede reasignar una tarea terminada."));

        TaskAssigneeUpdateRequest req = new TaskAssigneeUpdateRequest(3L);
        mockMvc.perform(patch("/tasks/2/assignee")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("No se puede reasignar una tarea terminada."));
    }

    @Test
    void patch_assignee_404_si_no_existe() throws Exception {
        when(taskService.buscarPorId(99L)).thenReturn(Optional.empty());

        TaskAssigneeUpdateRequest req = new TaskAssigneeUpdateRequest(2L);
        mockMvc.perform(patch("/tasks/99/assignee")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    @Test
    void patch_assignee_400_cuerpo_vacio_y_service_no_llamado() throws Exception {
        mockMvc.perform(patch("/tasks/6/assignee")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verify(taskService, never()).reasignar(any(), anyLong());
    }
}
