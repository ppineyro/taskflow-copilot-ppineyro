package com.taskflow.slice;

import com.taskflow.controller.ProjectController;
import com.taskflow.model.Project;
import com.taskflow.model.TaskStatus;
import com.taskflow.security.JwtAuthenticationFilter;
import com.taskflow.service.ProjectService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Slice web de GET /projects/{id}/summary (versión de referencia del Día 4): solo el contrato HTTP. */
@WebMvcTest(ProjectController.class)
@AutoConfigureMockMvc(addFilters = false)   // la seguridad no se prueba en el slice; el 401 de esta ruta lo comprueba verificar.ps1
class ProjectSummaryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProjectService projectService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test
    void getResumen_existente_devuelve200ConCadaCampo() throws Exception {
        when(projectService.buscarPorId(2L)).thenReturn(Optional.of(new Project(2L, "App Móvil", "d", 2L, null)));
        when(projectService.resumen(any(Project.class))).thenReturn(new com.taskflow.service.ProjectSummary(4,
                Map.of(TaskStatus.TODO, 1, TaskStatus.IN_PROGRESS, 2, TaskStatus.DONE, 1), 1));

        mockMvc.perform(get("/projects/2/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId").value(2))
                .andExpect(jsonPath("$.projectName").value("App Móvil"))
                .andExpect(jsonPath("$.totalTasks").value(4))
                .andExpect(jsonPath("$.byStatus.TODO").value(1))
                .andExpect(jsonPath("$.byStatus.IN_PROGRESS").value(2))
                .andExpect(jsonPath("$.byStatus.DONE").value(1))
                .andExpect(jsonPath("$.overdue").value(1));
    }

    @Test
    void getResumen_proyectoInexistente_devuelve404() throws Exception {
        when(projectService.buscarPorId(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/projects/99/summary"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void getResumen_proyectoSinTareas_devuelveCeros() throws Exception {
        when(projectService.buscarPorId(3L)).thenReturn(Optional.of(new Project(3L, "Migración Legacy", "d", 1L, null)));
        when(projectService.resumen(any(Project.class))).thenReturn(new com.taskflow.service.ProjectSummary(0,
                Map.of(TaskStatus.TODO, 0, TaskStatus.IN_PROGRESS, 0, TaskStatus.DONE, 0), 0));

        mockMvc.perform(get("/projects/3/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId").value(3))
                .andExpect(jsonPath("$.projectName").value("Migración Legacy"))
                .andExpect(jsonPath("$.totalTasks").value(0))
                .andExpect(jsonPath("$.byStatus.TODO").value(0))
                .andExpect(jsonPath("$.byStatus.IN_PROGRESS").value(0))
                .andExpect(jsonPath("$.byStatus.DONE").value(0))
                .andExpect(jsonPath("$.overdue").value(0));
    }
}
