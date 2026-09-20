# Copilot CLI Session

> [!NOTE]
> - **Session ID:** `3c2171c4-7440-4220-8470-3752541a9465`  
> - **Started:** 9/19/2026, 6:53:16 PM  
> - **Duration:** 2m 8s  
> - **Exported:** 9/19/2026, 6:55:25 PM  

---

<sub>2s</sub>

### User

Revisa evidencia/dia4/summary.diff contra la especificación specs/summary.md.

---

<sub>8s</sub>

### Copilot

Leeré el diff de evidencia/dia4/summary.diff y la especificación specs/summary.md para identificar archivos cambiados y comparar con la spec. Ejecutando lecturas de ambos archivos en paralelo.

---

<sub>8s</sub>

### `view`

**view the file at evidencia/dia4/summary.diff.**

evidencia/dia4/summary.diff

<details>
<summary>383 lines</summary>

```diff
diff --git a/src/main/java/com/taskflow/controller/ProjectController.java b/src/main/java/com/taskflow/controller/ProjectController.java
index 56ccccf..cff06df 100644
--- a/src/main/java/com/taskflow/controller/ProjectController.java
+++ b/src/main/java/com/taskflow/controller/ProjectController.java
@@ -2,6 +2,7 @@ package com.taskflow.controller;
 
 import com.taskflow.dto.ProjectRequest;
 import com.taskflow.dto.ProjectResponse;
+import com.taskflow.dto.ProjectSummaryResponse;
 import com.taskflow.dto.TaskResponse;
 import com.taskflow.exception.ProjectNotFoundException;
 import com.taskflow.mapper.ProjectMapper;
@@ -131,4 +132,15 @@ public class ProjectController {
         projectService.eliminar(id);
         return ResponseEntity.noContent().build();
     }
+
+    /** GET /projects/{id}/summary — 200 con el resumen; 404 uniforme si el proyecto no existe. */
+    @Operation(summary = "Resumen de un proyecto",
+            description = "Tareas por estado (siempre TODO, IN_PROGRESS y DONE) y cuántas están vencidas. 404 si el proyecto no existe.")
+    @GetMapping("/projects/{id}/summary")
+    public ProjectSummaryResponse getResumen(@PathVariable("id") Long id) {
+        Project proyecto = projectService.buscarPorId(id)
+                .orElseThrow(() -> new ProjectNotFoundException(id));
+        com.taskflow.service.ProjectSummary summary = projectService.resumen(proyecto);
+        return ProjectMapper.aResumen(proyecto, summary);
+    }
 }
diff --git a/src/main/java/com/taskflow/dto/ProjectSummaryResponse.java b/src/main/java/com/taskflow/dto/ProjectSummaryResponse.java
new file mode 100644
index 0000000..970175b
--- /dev/null
+++ b/src/main/java/com/taskflow/dto/ProjectSummaryResponse.java
@@ -0,0 +1,19 @@
+package com.taskflow.dto;
+
+import com.taskflow.model.TaskStatus;
+
+import java.util.Map;
+
+/**
+ * ProjectSummaryResponse — contrato de salida de GET /projects/{id}/summary (versión de referencia del Día 4).
+ *
+ * byStatus trae siempre las tres claves de TaskStatus (TODO, IN_PROGRESS, DONE), aunque valgan 0.
+ */
+public record ProjectSummaryResponse(
+        Long projectId,
+        String projectName,
+        long totalTasks,
+        Map<TaskStatus, Long> byStatus,
+        long overdue
+) {
+}
diff --git a/src/main/java/com/taskflow/mapper/ProjectMapper.java b/src/main/java/com/taskflow/mapper/ProjectMapper.java
index a33a95b..f438ffa 100644
--- a/src/main/java/com/taskflow/mapper/ProjectMapper.java
+++ b/src/main/java/com/taskflow/mapper/ProjectMapper.java
@@ -1,7 +1,12 @@
 package com.taskflow.mapper;
 
 import com.taskflow.dto.ProjectResponse;
+import com.taskflow.dto.ProjectSummaryResponse;
 import com.taskflow.model.Project;
+import com.taskflow.model.TaskStatus;
+
+import java.util.EnumMap;
+import java.util.Map;
 
 /**
  * ProjectMapper — puente DTO &lt;-&gt; dominio del lado Project. Estático, a mano, sin MapStruct.
@@ -22,4 +27,20 @@ public final class ProjectMapper {
         return new ProjectResponse(p.getId(), p.getName(), p.getDescription(),
                 p.getOwnerId(), p.getCreatedAt());
     }
+
+    /** Proyecto + conteos ya calculados por el service -> DTO de salida de GET /projects/{id}/summary. */
+    public static ProjectSummaryResponse aResumen(Project p, long totalTasks, Map<TaskStatus, Long> byStatus,
+                                                  long overdue) {
+        return new ProjectSummaryResponse(p.getId(), p.getName(), totalTasks, byStatus, overdue);
+    }
+
+    /** Sobrecarga: acepta el valor de servicio ProjectSummary y convierte los tipos (Integer -> Long). */
+    public static ProjectSummaryResponse aResumen(Project p, com.taskflow.service.ProjectSummary summary) {
+        Map<TaskStatus, Long> byStatusLong = new EnumMap<>(TaskStatus.class);
+        for (TaskStatus estado : TaskStatus.values()) {
+            Integer v = summary.byStatus().get(estado);
+            byStatusLong.put(estado, v == null ? 0L : v.longValue());
+        }
+        return new ProjectSummaryResponse(p.getId(), p.getName(), summary.totalTasks(), byStatusLong, summary.overdue());
+    }
 }
diff --git a/src/main/java/com/taskflow/service/ProjectService.java b/src/main/java/com/taskflow/service/ProjectService.java
index d315e3f..11f5223 100644
--- a/src/main/java/com/taskflow/service/ProjectService.java
+++ b/src/main/java/com/taskflow/service/ProjectService.java
@@ -1,9 +1,12 @@
 package com.taskflow.service;
 
 import com.taskflow.dto.ProjectRequest;
+import com.taskflow.dto.ProjectSummaryResponse;
 import com.taskflow.exception.ProjectNotFoundException;
+import com.taskflow.mapper.ProjectMapper;
 import com.taskflow.model.Project;
 import com.taskflow.model.Task;
+import com.taskflow.model.TaskStatus;
 import com.taskflow.model.User;
 import com.taskflow.repository.ProjectRepository;
 import com.taskflow.repository.TaskRepository;
@@ -12,7 +15,9 @@ import org.springframework.security.core.userdetails.UsernameNotFoundException;
 import org.springframework.stereotype.Service;
 
 import java.time.LocalDate;
+import java.util.EnumMap;
 import java.util.List;
+import java.util.Map;
 import java.util.Optional;
 
 /**
@@ -101,4 +106,19 @@ public class ProjectService {
         tareasDe(id).forEach(t -> taskRepository.deleteById(t.getId()));   // cascada manual (la FK obliga el orden)
         projectRepository.deleteById(id);
     }
+
+    /**
+     * Resumen de un proyecto que ya existe (el 404 lo resuelve el controller): cuántas tareas tiene en cada
+     * estado y cuántas están vencidas. Vencida = Task.estaVencida(); la regla no se reescribe aquí.
+     */
+    public com.taskflow.service.ProjectSummary resumen(Project proyecto) {
+        List<Task> tareas = taskRepository.findByProjectId(proyecto.getId());
+        Map<TaskStatus, Integer> porEstado = new EnumMap<>(TaskStatus.class);
+        for (TaskStatus estado : TaskStatus.values()) {
+            porEstado.put(estado, 0);                      // las tres claves siempre, aunque valgan 0
+        }
+        tareas.forEach(t -> porEstado.merge(t.getStatus(), 1, Integer::sum));
+        int vencidas = (int) tareas.stream().filter(Task::estaVencida).count();
+        return new com.taskflow.service.ProjectSummary(tareas.size(), porEstado, vencidas);
+    }
 }
diff --git a/src/main/java/com/taskflow/service/ProjectSummary.java b/src/main/java/com/taskflow/service/ProjectSummary.java
new file mode 100644
index 0000000..226f967
--- /dev/null
+++ b/src/main/java/com/taskflow/service/ProjectSummary.java
@@ -0,0 +1,11 @@
+package com.taskflow.service;
+
+import com.taskflow.model.TaskStatus;
+
+import java.util.Map;
+
+/**
+ * Valor de servicio que contiene los conteos del resumen de proyecto.
+ */
+public record ProjectSummary(int totalTasks, Map<TaskStatus, Integer> byStatus, int overdue) {
+}
diff --git a/src/test/java/com/taskflow/slice/ProjectSummaryControllerTest.java b/src/test/java/com/taskflow/slice/ProjectSummaryControllerTest.java
new file mode 100644
index 0000000..db1920e
--- /dev/null
+++ b/src/test/java/com/taskflow/slice/ProjectSummaryControllerTest.java
@@ -0,0 +1,63 @@
+package com.taskflow.slice;
+
+import com.taskflow.controller.ProjectController;
+import com.taskflow.model.Project;
+import com.taskflow.model.TaskStatus;
+import com.taskflow.security.JwtAuthenticationFilter;
+import com.taskflow.service.ProjectService;
+import org.junit.jupiter.api.Test;
+import org.springframework.beans.factory.annotation.Autowired;
+import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
+import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
+import org.springframework.test.context.bean.override.mockito.MockitoBean;
+import org.springframework.test.web.servlet.MockMvc;
+
+import java.util.Map;
+import java.util.Optional;
+
+import static org.mockito.ArgumentMatchers.any;
+import static org.mockito.Mockito.when;
+import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
+import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
+import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
+
+/** Slice web de GET /projects/{id}/summary (versión de referencia del Día 4): solo el contrato HTTP. */
+@WebMvcTest(ProjectController.class)
+@AutoConfigureMockMvc(addFilters = false)   // la seguridad no se prueba en el slice; el 401 de esta ruta lo comprueba verificar.ps1
+class ProjectSummaryControllerTest {
+
+    @Autowired
+    private MockMvc mockMvc;
+
+    @MockitoBean
+    private ProjectService projectService;
+
+    @MockitoBean
+    private JwtAuthenticationFilter jwtAuthenticationFilter;
+
+    @Test
+    void getResumen_existente_devuelve200ConCadaCampo() throws Exception {
+        when(projectService.buscarPorId(2L)).thenReturn(Optional.of(new Project(2L, "App Móvil", "d", 2L, null)));
+        when(projectService.resumen(any(Project.class))).thenReturn(new com.taskflow.service.ProjectSummary(4,
+                Map.of(TaskStatus.TODO, 1, TaskStatus.IN_PROGRESS, 2, TaskStatus.DONE, 1), 1));
+
+        mockMvc.perform(get("/projects/2/summary"))
+                .andExpect(status().isOk())
+                .andExpect(jsonPath("$.projectId").value(2))
+                .andExpect(jsonPath("$.projectName").value("App Móvil"))
+                .andExpect(jsonPath("$.totalTasks").value(4))
+                .andExpect(jsonPath("$.byStatus.TODO").value(1))
+                .andExpect(jsonPath("$.byStatus.IN_PROGRESS").value(2))
+                .andExpect(jsonPath("$.byStatus.DONE").value(1))
+                .andExpect(jsonPath("$.overdue").value(1));
+    }
+
+    @Test
+    void getResumen_proyectoInexistente_devuelve404() throws Exception {
+        when(projectService.buscarPorId(99L)).thenReturn(Optional.empty());
+
+        mockMvc.perform(get("/projects/99/summary"))
+                .andExpect(status().isNotFound())
+                .andExpect(jsonPath("$.status").value(404));
+    }
+}
diff --git a/src/test/java/com/taskflow/unit/ProjectServiceTest.java b/src/test/java/com/taskflow/unit/ProjectServiceTest.java
new file mode 100644
index 0000000..ff681ef
--- /dev/null
+++ b/src/test/java/com/taskflow/unit/ProjectServiceTest.java
@@ -0,0 +1,83 @@
+package com.taskflow.unit;
+
+import com.taskflow.exception.TaskValidationException;
+import com.taskflow.model.Priority;
+import com.taskflow.model.Project;
+import com.taskflow.model.Task;
+import com.taskflow.model.TaskStatus;
+import com.taskflow.repository.ProjectRepository;
+import com.taskflow.repository.TaskRepository;
+import com.taskflow.repository.UserRepository;
+import com.taskflow.service.ProjectService;
+import com.taskflow.service.ProjectSummary;
+import org.junit.jupiter.api.Test;
+import org.junit.jupiter.api.extension.ExtendWith;
+import org.mockito.InjectMocks;
+import org.mockito.Mock;
+import org.mockito.junit.jupiter.MockitoExtension;
+
+import java.time.LocalDate;
+import java.util.List;
+import java.util.Map;
+import java.util.Optional;
+
+import static org.assertj.core.api.Assertions.assertThat;
+import static org.junit.jupiter.api.Assertions.assertEquals;
+import static org.mockito.Mockito.when;
+
+@ExtendWith(MockitoExtension.class)
+class ProjectServiceTest {
+
+    @Mock
+    private ProjectRepository projectRepository;
+
+    @Mock
+    private TaskRepository taskRepository;
+
+    @Mock
+    private UserRepository userRepository;
+
+    @InjectMocks
+    private ProjectService service;
+
+    @Test
+    void resumen_conTareas_calculaConteosYVencidas() throws Exception {
+        Project proyecto = new Project(2L, "App Móvil", "d", 2L, null);
+        Task t1 = tarea(3L, "Tarea 3", TaskStatus.TODO, Priority.MED, null, null);
+        Task t2 = tarea(4L, "Tarea 4", TaskStatus.IN_PROGRESS, Priority.HIGH, null, LocalDate.now().plusDays(5));
+        Task t3 = tarea(5L, "Tarea 5", TaskStatus.IN_PROGRESS, Priority.LOW, null, LocalDate.now().minusDays(1)); // vencida
+        Task t4 = tarea(6L, "Tarea 6", TaskStatus.DONE, Priority.MED, null, LocalDate.now().minusDays(2));
+
+        when(taskRepository.findByProjectId(2L)).thenReturn(List.of(t1, t2, t3, t4));
+
+        ProjectSummary summary = service.resumen(proyecto);
+
+        assertEquals(4, summary.totalTasks());
+        assertThat(summary.byStatus()).containsEntry(TaskStatus.TODO, 1);
+        assertThat(summary.byStatus()).containsEntry(TaskStatus.IN_PROGRESS, 2);
+        assertThat(summary.byStatus()).containsEntry(TaskStatus.DONE, 1);
+        assertEquals(1, summary.overdue());
+    }
+
+    @Test
+    void resumen_proyectoSinTareas_devuelveCeros() {
+        Project proyecto = new Project(3L, "Empty", "d", 1L, null);
+        when(taskRepository.findByProjectId(3L)).thenReturn(List.of());
+
+        ProjectSummary summary = service.resumen(proyecto);
+
+        assertEquals(0, summary.totalTasks());
+        assertThat(summary.byStatus()).containsEntry(TaskStatus.TODO, 0);
+        assertThat(summary.byStatus()).containsEntry(TaskStatus.IN_PROGRESS, 0);
+        assertThat(summary.byStatus()).containsEntry(TaskStatus.DONE, 0);
+        assertEquals(0, summary.overdue());
+    }
+
+    private Task tarea(Long id, String title, TaskStatus status, Priority priority, Long assigneeId, LocalDate dueDate) {
+        try {
+            return new Task(id, title, "desc", status, priority, 2L, assigneeId, dueDate);
+        } catch (TaskValidationException e) {
+            throw new IllegalStateException("dato de prueba inválido", e);
+        }
+    }
+}
diff --git a/src/test/java/com/taskflow/unit/ProjectSummaryServiceTest.java b/src/test/java/com/taskflow/unit/ProjectSummaryServiceTest.java
new file mode 100644
index 0000000..50b03f5
--- /dev/null
+++ b/src/test/java/com/taskflow/unit/ProjectSummaryServiceTest.java
@@ -0,0 +1,70 @@
+package com.taskflow.unit;
+
+import com.taskflow.dto.ProjectSummaryResponse;
+import com.taskflow.exception.TaskValidationException;
+import com.taskflow.model.Priority;
+import com.taskflow.model.Project;
+import com.taskflow.model.Task;
+import com.taskflow.model.TaskStatus;
+import com.taskflow.repository.ProjectRepository;
+import com.taskflow.repository.TaskRepository;
+import com.taskflow.repository.UserRepository;
+import com.taskflow.service.ProjectService;
+import org.junit.jupiter.api.Test;
+import org.junit.jupiter.api.extension.ExtendWith;
+import org.mockito.InjectMocks;
+import org.mockito.Mock;
+import org.mockito.junit.jupiter.MockitoExtension;
+
+import java.time.LocalDate;
+import java.util.List;
+import java.util.Map;
+
+import static org.junit.jupiter.api.Assertions.assertEquals;
+import static org.mockito.Mockito.when;
+
+/** Unit de ProjectService.resumen (versión de referencia del Día 4): sin Spring, tareas reales. */
+@ExtendWith(MockitoExtension.class)
+class ProjectSummaryServiceTest {
+
+    @Mock
+    private ProjectRepository projectRepository;
+
+    @Mock
+    private TaskRepository taskRepository;
+
+    @Mock
+    private UserRepository userRepository;
+
+    @InjectMocks
+    private ProjectService service;
+
+    private final Project appMovil = new Project(2L, "App Móvil", "d", 2L, null);
+
+    @Test
+    void resumen_cuentaPorEstadoYVencidas() throws TaskValidationException {
+        LocalDate ayer = LocalDate.now().minusDays(1);
+        when(taskRepository.findByProjectId(2L)).thenReturn(List.of(
+                tarea(6L, TaskStatus.TODO, LocalDate.now().plusDays(10)),
+                tarea(7L, TaskStatus.IN_PROGRESS, ayer),          // vencida
+                tarea(8L, TaskStatus.DONE, ayer.minusDays(4)),    // fecha pasada pero DONE: NO vence
+                tarea(9L, TaskStatus.IN_PROGRESS, null)));        // sin fecha: NO vence
+
+        // Contado a mano: TODO 1, IN_PROGRESS 2, DONE 1; vencida solo la 7 (igual que el proyecto 2 de la semilla)
+        assertEquals(new com.taskflow.service.ProjectSummary(4, Map.of(TaskStatus.TODO, 1, TaskStatus.IN_PROGRESS, 2, TaskStatus.DONE, 1), 1),
+                service.resumen(appMovil));
+    }
+
+    @Test
+    void resumen_proyectoSinTareas_todoEnCeroYLasTresClaves() {
+        Project legacy = new Project(3L, "Migración Legacy", "d", 1L, null);
+        when(taskRepository.findByProjectId(3L)).thenReturn(List.of());
+
+        assertEquals(new com.taskflow.service.ProjectSummary(0, Map.of(TaskStatus.TODO, 0, TaskStatus.IN_PROGRESS, 0, TaskStatus.DONE, 0), 0),
+                service.resumen(legacy));
+    }
+
+    private Task tarea(Long id, TaskStatus estado, LocalDate fecha) throws TaskValidationException {
+        return new Task(id, "Tarea " + id, "d", estado, Priority.MED, 2L, 1L, fecha);
+    }
+}
```

</details>


---

<sub>8s</sub>

### `view`

**view the file at specs/summary.md.**

specs/summary.md

<details>
<summary>52 lines</summary>

````
## Qué se pide

Un endpoint nuevo, `GET /projects/{id}/summary`, que resume un proyecto: **cuántas tareas tiene en
cada estado y cuántas están vencidas**. Sirve para pintar un tablero sin descargar todas las tareas del
proyecto. Pide el token de `POST /auth/login`, como todo lo que no es público.

## Respuesta 200

```json
{
  "projectId": 2,
  "projectName": "App Móvil",
  "totalTasks": 4,
  "byStatus": { "TODO": 1, "IN_PROGRESS": 2, "DONE": 1 },
  "overdue": 1
}
```

| Campo | Tipo | Qué es |
|---|---|---|
| `projectId` | número | el `id` del proyecto pedido |
| `projectName` | texto | el `name` del proyecto |
| `totalTasks` | número | cuántas tareas tiene el proyecto |
| `byStatus` | objeto | una clave por cada valor de `TaskStatus` (`TODO`, `IN_PROGRESS`, `DONE`), **siempre las tres**, aunque valgan 0 |
| `overdue` | número | cuántas tareas del proyecto cumplen `Task.estaVencida()` |

## Reglas

1. **Vencida** es exactamente lo que ya dice `Task.estaVencida()`: tiene fecha límite, la fecha ya pasó y
   el estado no es `DONE`. Se reutiliza ese método; no se reescribe la regla en el servicio.
2. Un proyecto **sin tareas** responde 200 con `totalTasks` 0, las tres claves de `byStatus` en 0 y `overdue` 0.
3. Un proyecto **que no existe** responde **404** con el `ErrorResponse` uniforme de siempre
   (`ProjectNotFoundException`, igual que `GET /projects/{id}`).
4. **Sin token** responde **401**, como todo lo que no es público.
5. Cualquier usuario autenticado puede pedir el resumen de cualquier proyecto (igual que `GET /projects/{id}/tasks`).

## Resultado esperado con la semilla del perfil `h2`

| Petición | `totalTasks` | `byStatus` | `overdue` |
|---|---|---|---|
| `GET /projects/1/summary` | 5 | `TODO` 3 · `IN_PROGRESS` 1 · `DONE` 1 | 0 (la tarea 2 tiene fecha pasada, pero está `DONE`) |
| `GET /projects/2/summary` | 4 | `TODO` 1 · `IN_PROGRESS` 2 · `DONE` 1 | 1 (la tarea 7) |
| `GET /projects/3/summary` | 0 | `TODO` 0 · `IN_PROGRESS` 0 · `DONE` 0 | 0 |
| `GET /projects/99/summary` | — | — | 404 |

Si antes de probar creaste tareas en un proyecto (por la UI o con el servidor MCP), `totalTasks` y
`TODO` de ese proyecto suben en la misma cantidad. Al reiniciar la app con el perfil `h2` vuelven a
ser los de la tabla.

## Cómo se construye (convenciones de TaskFlow)

- DTO de salida: un `record` en `com.taskflow.dto`, `ProjectSummaryResponse`.
- La lógica de conteo va en el servicio (`ProjectService`), no en el controller.
- El controller solo traduce HTTP: busca el proyecto (404 si no existe) y delega.
- Tests:
  - **unit** del servicio con Mockito: un proyecto con tareas en los tres estados y una vencida; un proyecto sin tareas.
  - **slice** `@WebMvcTest` del controller: 200 con el JSON de arriba y 404 con un proyecto inexistente.
- `mvn -q test` en verde al terminar, sin modificar tests existentes.

## Criterios de aceptación

- [ ] `GET /projects/2/summary` con el token de `ana` devuelve el JSON del ejemplo.
- [ ] `GET /projects/3/summary` devuelve ceros en los cinco números.
- [ ] `GET /projects/99/summary` devuelve 404.
- [ ] `mvn -q test` en verde: los tests de antes más los nuevos.
````

</details>


---

<sub>20s</sub>

### Copilot

Abriré los archivos clave (controller, service, mapper y modelo Task/TaskStatus) para comparar el diff con la implementación actual y comprobar la reutilización de Task.estaVencida(). Ejecutando lecturas en paralelo.

---

<sub>20s</sub>

### `view`

**view the file at src/main/java/com/taskflow/controller/ProjectController.java.**

src/main/java/com/taskflow/controller/ProjectController.java

<details>
<summary>134 lines</summary>

```
package com.taskflow.controller;

import com.taskflow.dto.ProjectRequest;
import com.taskflow.dto.ProjectResponse;
import com.taskflow.dto.ProjectSummaryResponse;
import com.taskflow.dto.TaskResponse;
import com.taskflow.exception.ProjectNotFoundException;
import com.taskflow.mapper.ProjectMapper;
import com.taskflow.mapper.TaskMapper;
import com.taskflow.model.Project;
import com.taskflow.model.Task;
import com.taskflow.model.TaskStatus;
import com.taskflow.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

/**
 * ProjectController — la puerta HTTP de los proyectos. HOY (integrador) crece a CRUD completo, en
 * DTOs (ProjectRequest / ProjectResponse); las tareas de un proyecto salen como TaskResponse.
 *
 * Repetición deliberada del patrón de TaskController: mismos gestos (201 + Location, @Valid, 404 vía
 * orElseThrow -> advice). El lado Project cierra la tabla de endpoints del capstone (menos /auth/*).
 */
@RestController
@Tag(name = "Projects", description = "CRUD de proyectos y sus tareas. Borrar un proyecto arrastra sus tareas (cascada).")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    /** GET /projects — todos los proyectos como ProjectResponse. */
    @Operation(summary = "Lista todos los proyectos",
            description = "Devuelve todos los proyectos como ProjectResponse (lista vacía si no hay ninguno).")
    @GetMapping("/projects")
    public List<ProjectResponse> getProjects() {
        return projectService.listar().stream()
                .map(ProjectMapper::aResponse)
                .toList();
    }

    /** GET /projects/{id} — 200 con ProjectResponse; 404 uniforme si no existe. */
    @Operation(summary = "Obtiene un proyecto por id",
            description = "200 con el ProjectResponse; 404 uniforme si el id no existe.")
    @GetMapping("/projects/{id}")
    public ProjectResponse getProject(@PathVariable("id") Long id) {
        Project proyecto = projectService.buscarPorId(id)
                .orElseThrow(() -> new ProjectNotFoundException(id));
        return ProjectMapper.aResponse(proyecto);
    }

    /**
     * GET /projects/{id}/tasks — las tareas de un proyecto como TaskResponse. Conserva la distinción
     * de D2: proyecto inexistente -> 404 (orElseThrow); proyecto sin tareas -> 200 con []. El filtro
     * ?status= es STRETCH (mismo enum que /tasks). Sigue delegando en ProjectService.tareasDe.
     */
    @Operation(summary = "Lista las tareas de un proyecto",
            description = "Tareas del proyecto como TaskResponse; 404 si el proyecto no existe, 200 con [] si no tiene tareas. Filtro opcional ?status= (stretch).")
    @GetMapping("/projects/{id}/tasks")
    public List<TaskResponse> getTareasDeProyecto(
            @PathVariable("id") Long id,
            @RequestParam(name = "status", required = false) TaskStatus status) {   // status: STRETCH
        projectService.buscarPorId(id)
                .orElseThrow(() -> new ProjectNotFoundException(id));
        List<Task> tareas = projectService.tareasDe(id);
        if (status != null) {                          // STRETCH: filtro opcional por estado
            tareas = tareas.stream().filter(t -> t.getStatus() == status).toList();
        }
        return tareas.stream().map(TaskMapper::aResponse).toList();
    }

    /**
     * POST /projects — 201 + Location a /projects/{id}. @Valid dispara Bean Validation (400 si falla).
     * MP-9: el owner sale del JWT — el Authentication (inyectado por Spring Security) trae el username
     * del token; el service lo resuelve a ownerId. El dueño es QUIEN crea, no una constante.
     */
    @Operation(summary = "Crea un proyecto",
            description = "El owner se toma del usuario autenticado (el username del JWT).")
    @PostMapping("/projects")
    public ResponseEntity<ProjectResponse> createProject(@Valid @RequestBody ProjectRequest request,
                                                         Authentication authentication) {
        Project creado = projectService.crear(request, authentication.getName());
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/projects/{id}")
                .buildAndExpand(creado.getId())
                .toUri();
        return ResponseEntity.created(location).body(ProjectMapper.aResponse(creado));
    }

    /** PUT /projects/{id} — reemplazo COMPLETO. 200 con ProjectResponse; 404 si no existe. */
    @Operation(summary = "Reemplaza un proyecto",
            description = "Conserva id, owner y createdAt; name y description vienen del cuerpo.")
    @PutMapping("/projects/{id}")
    public ProjectResponse updateProject(@PathVariable("id") Long id,
                                         @Valid @RequestBody ProjectRequest request) {
        Project actualizado = projectService.reemplazar(id, request);
        return ProjectMapper.aResponse(actualizado);
    }

    /**
     * DELETE /projects/{id} — 204 No Content; 404 si no existe. Borra en cascada las tareas del
     * proyecto (regla "no Task sin Project").
     *
     * MP-9: la regla del capstone EN SERIO. @PreAuthorize se evalúa ANTES del método: pasa si el
     * usuario es ADMIN o es el owner del proyecto (bean @projectSecurity, data-driven). Un USER que no
     * es owner -> 403 (la API SÍ sabe quién es, por eso 403 y no 401). Proyecto inexistente ->
     * esOwner devuelve true a propósito para que hable el 404 del servicio.
     */
    @Operation(summary = "Borra un proyecto y sus tareas",
            description = "Solo ADMIN o el owner. Un USER no-owner recibe 403.")
    @PreAuthorize("hasRole('ADMIN') or @projectSecurity.esOwner(#id, authentication.name)")
    @DeleteMapping("/projects/{id}")
    public ResponseEntity<Void> deleteProject(@PathVariable("id") Long id) {
        projectService.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    /** GET /projects/{id}/summary — 200 con el resumen; 404 uniforme si el proyecto no existe. */
    @Operation(summary = "Resumen de un proyecto",
            description = "Tareas por estado (siempre TODO, IN_PROGRESS y DONE) y cuántas están vencidas. 404 si el proyecto no existe.")
    @GetMapping("/projects/{id}/summary")
    public ProjectSummaryResponse getResumen(@PathVariable("id") Long id) {
        Project proyecto = projectService.buscarPorId(id)
                .orElseThrow(() -> new ProjectNotFoundException(id));
        com.taskflow.service.ProjectSummary summary = projectService.resumen(proyecto);
        return ProjectMapper.aResumen(proyecto, summary);
    }
}
```

</details>


---

<sub>23s</sub>

### Copilot

Abriré Task.java y TaskStatus.java para comprobar que Task.estaVencida() existe y su firma, y revisar ProjectMapper/ProjectService integraciones. Ejecutando lecturas.

---

<sub>23s</sub>

### `view`

**view the file at src/main/java/com/taskflow/model/Task.java.**

src/main/java/com/taskflow/model/Task.java

<details>
<summary>225 lines</summary>

```
package com.taskflow.model;

import com.taskflow.exception.TaskValidationException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Task — entidad central del dominio TaskFlow. ESTADO FINAL del Día 4 (persistida con JPA).
 *
 * La entidad ES la clase que YA teníamos (canónica S1, 8 campos): NO se escribió un "modelo nuevo",
 * se ANOTÓ el Task de D3. Novedades de HOY, todas por anotación:
 *   - @Entity + @Table(name = "tasks"): esta clase mapea a la tabla TASKS.
 *   - @Id + @GeneratedValue(IDENTITY): el id lo asigna la BD (columna autoincremental). La secuencia
 *     manual del InMemoryTaskRepository (el Math.max de S1D3) MURIÓ: el id lo pone la BD y nadie más.
 *   - @Enumerated(STRING) en status y priority: se guardan como TEXTO ('TODO'), NUNCA como ORDINAL
 *     (0,1,2): reordenar o insertar un enum corrompería datos históricos. El orden LOW,MED,HIGH de
 *     S1D3 es contrato del SORT (compareTo), no del almacenamiento.
 *   - relación @ManyToOne Task->Project (patrón "asociación de solo lectura"): la COLUMNA la sigue
 *     mandando el escalar 'projectId' (por eso Task.crear, TaskService, mappers y DTOs NO cambian);
 *     el objeto 'project' solo NAVEGA. Frase ancla: "la columna manda; el objeto navega".
 *
 * Tensión JPA vs validaciones (paga de S1D2 MP-8, fijada por el apéndice del CAPSTONE): JPA rehidrata
 * por reflexión con un constructor sin args protegido, SIN pasar por las validaciones. Está bien:
 * Task.crear(...) sigue siendo el ÚNICO camino de CREACIÓN de negocio; JPA solo REHIDRATA datos que
 * ya existían (la misma distinción crear/rehidratar de siempre).
 *
 * OJO: las columnas persistentes NO pueden ser 'final' (JPA las escribe por reflexión tras el no-arg);
 * por eso title/description/projectId/dueDate perdieron el 'final' que traían de D3. La inmutabilidad
 * de negocio la sigue garantizando la ausencia de setters públicos para esos campos.
 */
@Entity
@Table(name = "tasks")
public class Task implements Comparable<Task> {

    // Reglas de longitud del título (del capstone).
    private static final int TITULO_MIN = 3;
    private static final int TITULO_MAX = 120;

    // id nace null; lo asigna la BD (IDENTITY). Ver setId (uso interno de JPA/repo).
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String title;

    private String description;                 // por convención -> columna DESCRIPTION

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskStatus status;                  // texto 'TODO'/'IN_PROGRESS'/'DONE' (nunca 0/1/2)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Priority priority;                  // texto 'LOW'/'MED'/'HIGH'

    // La COLUMNA project_id la posee este escalar (insertable/updatable por default). Sigue siendo
    // el dueño: Task.crear, el mapper y el servicio hablan con projectId, no con el objeto.
    @Column(name = "project_id", nullable = false)
    private Long projectId;

    // assigneeId se queda Long a secas (juicio explícito): es nullable, NUNCA navegamos al User
    // completo al leer tareas, y las derived queries operan igual. No toda FK necesita ser objeto:
    // mapea objeto cuando NAVEGAS, deja el id cuando solo REFERENCIAS.
    @Column(name = "assignee_id")
    private Long assigneeId;

    private LocalDate dueDate;                  // por convención -> columna DUE_DATE (camelCase->snake_case)

    /**
     * Asociación de SOLO LECTURA hacia el proyecto (MP-8). Comparte la MISMA columna project_id con
     * el escalar de arriba, pero marcada insertable=false/updatable=false: no escribe la columna (el
     * escalar lo hace), solo permite NAVEGAR (task.getProject().getName()). LAZY: no se carga hasta
     * que se toca — y fuera de una sesión abierta, tocarla lanza LazyInitializationException (por eso
     * la API responde con DTOs, nunca con la entidad).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", insertable = false, updatable = false)
    private Project project;

    /**
     * Constructor NO-ARG protegido: EXCLUSIVO de JPA (rehidratación por reflexión). Nunca es un camino
     * de creación de negocio. Su existencia es obligatoria para cualquier @Entity: sin él, al arrancar
     * Hibernate lanza "No default constructor for entity: com.taskflow.model.Task".
     */
    protected Task() {
        // solo para JPA: rehidratación, nunca creación de negocio
    }

    /**
     * Constructor de REHIDRATACIÓN: reconstruye una tarea que YA existía (semilla del DataSeeder,
     * datos releídos). Valida las INVARIANTES que siempre deben cumplirse (título y proyecto), pero
     * NO la regla temporal de dueDate: una tarea vencida es un dato válido que hay que poder releer.
     */
    public Task(Long id, String title, String description, TaskStatus status,
                Priority priority, Long projectId, Long assigneeId, LocalDate dueDate)
            throws TaskValidationException {
        // Invariante 1: título obligatorio, 3-120 chars.
        if (title == null || title.isBlank()) {
            throw new TaskValidationException("El título es obligatorio (no puede ir vacío).");
        }
        if (title.length() < TITULO_MIN || title.length() > TITULO_MAX) {
            throw new TaskValidationException(
                    "El título debe tener entre " + TITULO_MIN + " y " + TITULO_MAX
                            + " caracteres; recibí " + title.length() + ": \"" + title + "\".");
        }
        // Invariante 2: una tarea no puede existir sin proyecto (regla del capstone).
        if (projectId == null) {
            throw new TaskValidationException(
                    "Una tarea no puede existir sin proyecto (projectId == null).");
        }
        this.id = id;
        this.title = title;
        this.description = description;
        this.status = status;
        this.priority = priority;
        this.projectId = projectId;
        this.assigneeId = assigneeId;
        this.dueDate = dueDate;
    }

    /**
     * Factory de CREACIÓN de negocio: añade la regla temporal (dueDate no en el pasado) y delega en
     * el constructor. id nace null, status nace TODO. Firma canónica S1 (apéndice del CAPSTONE-SPEC):
     * la usan el mapper y el servicio tal cual — por eso NO cambia hoy pese a que Task ya sea @Entity.
     */
    public static Task crear(String title, String description, Priority priority,
                             LocalDate dueDate, Long projectId, Long assigneeId)
            throws TaskValidationException {
        if (dueDate != null && dueDate.isBefore(LocalDate.now())) {
            throw new TaskValidationException(
                    "La fecha límite no puede estar en el pasado: " + dueDate + ".");
        }
        return new Task(null, title, description, TaskStatus.TODO, priority, projectId, assigneeId, dueDate);
    }

    // ---- Comportamiento: vive CON los datos ----

    /** true si tiene fecha, ya pasó, y la tarea aún no está terminada. */
    public boolean estaVencida() {
        return dueDate != null && dueDate.isBefore(LocalDate.now()) && status != TaskStatus.DONE;
    }

    // ---- Getters ----

    public Long getId() {
        return id;
    }

    /** Uso interno (JPA/repo) para el id autoincremental. Nadie más lo llama a mano. */
    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public Priority getPriority() {
        return priority;
    }

    public Long getProjectId() {
        return projectId;
    }

    public Long getAssigneeId() {
        return assigneeId;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    /**
     * Navega al proyecto asociado (asociación de solo lectura). Fuera de una sesión JPA abierta puede
     * lanzar LazyInitializationException (por eso la API expone DTOs, no la entidad). En el runtime
     * nadie del camino principal lo usa; existe para NAVEGAR cuando haga falta.
     */
    public Project getProject() {
        return project;
    }

    // ---- Setters SOLO donde el dominio muta ----

    /**
     * Cambia el estado aplicando la regla del capstone: no se puede pasar a DONE una tarea sin
     * responsable (assigneeId). La regla vive AQUÍ, no en el servicio ni en el menú.
     */
    public void setStatus(TaskStatus status) throws TaskValidationException {
        if (status == TaskStatus.DONE && assigneeId == null) {
            throw new TaskValidationException(
                    "No se puede marcar como " + TaskStatus.DONE
                            + " una tarea sin responsable: \"" + title + "\".");
        }
        this.status = status;
    }

    public void setPriority(Priority priority) {
        this.priority = priority;
    }

    public void setAssigneeId(Long assigneeId) {
        this.assigneeId = assigneeId;
    }

    // ---- Identidad de ENTIDAD por id (S1D3 MP-6 = literalmente lo que hace JPA) ----

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Task otra = (Task) o;
        return Objects.equals(id, otra.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    // ---- Orden natural por prioridad (Comparable) ----

    @Override
    public int compareTo(Task otra) {
        return this.priority.compareTo(otra.priority);
    }

    @Override
    public String toString() {
        return "Task{id=" + id
                + ", title='" + title + '\''
                + ", status=" + status
                + ", priority=" + priority
                + ", assigneeId=" + (assigneeId == null ? "sin asignar" : assigneeId)
                + ", dueDate=" + (dueDate == null ? "sin fecha" : dueDate)
                + (estaVencida() ? " *VENCIDA*" : "")
                + '}';
    }
}
```

</details>


---

<sub>49s</sub>

### Copilot

Running reads to check test annotations across the repo to validate @MockitoBean usage and consistency. I'll list src/test/java/com/taskflow files.

---

<sub>49s</sub>

### `view`

**view the file at src/test/java/com/taskflow.**

src/test/java/com/taskflow (lines 1--1)

```
integration
slice
unit
```


---

<sub>1m 11s</sub>

### `view`

**view the file at src/main/java/com/taskflow/mapper/ProjectMapper.java.**

src/main/java/com/taskflow/mapper/ProjectMapper.java

<details>
<summary>39 lines</summary>

```
package com.taskflow.mapper;

import com.taskflow.dto.ProjectResponse;
import com.taskflow.dto.ProjectSummaryResponse;
import com.taskflow.model.Project;
import com.taskflow.model.TaskStatus;

import java.util.EnumMap;
import java.util.Map;

/**
 * ProjectMapper — puente DTO &lt;-&gt; dominio del lado Project. Estático, a mano, sin MapStruct.
 *
 * HOY se SIMPLIFICÓ (lo prometía D3): la entidad ya guarda 'ownerId' directo (se aplanó el 'User
 * owner' en MP-4), así que aResponse ya no deriva el id desde un objeto (p.getOwner().id()) — lee
 * p.getOwnerId() tal cual. El contrato de salida (ProjectResponse con ownerId Long) no cambió; el
 * mapeo se volvió trivial porque el dominio por fin coincide con la forma canónica.
 */
public final class ProjectMapper {

    private ProjectMapper() {
        // no instanciable
    }

    /** Entidad -> DTO de salida. Ahora ownerId sale directo del campo (sin puente por objeto). */
    public static ProjectResponse aResponse(Project p) {
        return new ProjectResponse(p.getId(), p.getName(), p.getDescription(),
                p.getOwnerId(), p.getCreatedAt());
    }

    /** Proyecto + conteos ya calculados por el service -> DTO de salida de GET /projects/{id}/summary. */
    public static ProjectSummaryResponse aResumen(Project p, long totalTasks, Map<TaskStatus, Long> byStatus,
                                                  long overdue) {
        return new ProjectSummaryResponse(p.getId(), p.getName(), totalTasks, byStatus, overdue);
    }

    /** Sobrecarga: acepta el valor de servicio ProjectSummary y convierte los tipos (Integer -> Long). */
    public static ProjectSummaryResponse aResumen(Project p, com.taskflow.service.ProjectSummary summary) {
        Map<TaskStatus, Long> byStatusLong = new EnumMap<>(TaskStatus.class);
        for (TaskStatus estado : TaskStatus.values()) {
            Integer v = summary.byStatus().get(estado);
            byStatusLong.put(estado, v == null ? 0L : v.longValue());
        }
        return new ProjectSummaryResponse(p.getId(), p.getName(), summary.totalTasks(), byStatusLong, summary.overdue());
    }
}
```

</details>


---

<sub>1m 34s</sub>

### `view`

**view the file at src/test/java/com/taskflow/slice/ProjectControllerTest.java.**

src/test/java/com/taskflow/slice/ProjectControllerTest.java

<details>
<summary>121 lines</summary>

```
package com.taskflow.slice;

import com.taskflow.controller.ProjectController;
import com.taskflow.exception.TaskValidationException;
import com.taskflow.model.Priority;
import com.taskflow.model.Project;
import com.taskflow.model.Task;
import com.taskflow.model.TaskStatus;
import com.taskflow.security.JwtAuthenticationFilter;
import com.taskflow.service.ProjectService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ProjectControllerTest — SLICE web (S3D1, MP-7). MISMO tratamiento canónico que TaskControllerTest:
 * @WebMvcTest + @MockitoBean del service + addFilters=false + @MockitoBean del filtro JWT.
 *
 * La seguridad (owner/ADMIN en el DELETE, 401/403) NO se prueba aquí: vive en integration/SecurityRulesTest
 * con token real. En el slice solo se verifica el CONTRATO web (200/201/400/404 + JSON).
 *
 * Nota del principal (mención de MP-6): createProject lee el username del Authentication; con
 * addFilters=false NO corre el filtro de seguridad que normalmente puebla request.getUserPrincipal(),
 * así que aquí se inyecta el principal directo en el request con .principal(...). (@WithMockUser sería
 * la alternativa cuando se quiere method security en el slice; el material canoniza addFilters=false y
 * deja la seguridad a integration/.)
 */
@WebMvcTest(ProjectController.class)
@AutoConfigureMockMvc(addFilters = false)
class ProjectControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProjectService projectService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    // ==================== GET ====================

    @Test
    void getProjects_retorna200YLista() throws Exception {
        when(projectService.listar()).thenReturn(List.of(
                new Project(1L, "Plataforma", "d", 1L, null),
                new Project(2L, "App Móvil", "d", 2L, null),
                new Project(3L, "Legacy", "d", 1L, null)));

        mockMvc.perform(get("/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void getProject_inexistente_retorna404() throws Exception {
        when(projectService.buscarPorId(999L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/projects/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void getTareasDeProyecto_conTareas_retorna200() throws Exception {
        when(projectService.buscarPorId(1L)).thenReturn(Optional.of(new Project(1L, "P", "d", 1L, null)));
        when(projectService.tareasDe(1L)).thenReturn(List.of(
                tarea(1L, "T-1"), tarea(2L, "T-2")));

        mockMvc.perform(get("/projects/1/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    // ==================== Escritura ====================

    @Test
    void postProject_valido_devuelve201ConLocation() throws Exception {
        // authentication.getName() = "ana" (lo pone .principal(...)). El service resuelve el ownerId.
        Authentication ana = new UsernamePasswordAuthenticationToken("ana", null, List.of());
        when(projectService.crear(any(), eq("ana")))
                .thenReturn(new Project(5L, "Nuevo proyecto de prueba", "Creado desde el test", 1L, null));

        String body = """
                {
                  "name": "Nuevo proyecto de prueba",
                  "description": "Creado desde el test"
                }
                """;

        mockMvc.perform(post("/projects")
                        .principal(ana)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern(".*/projects/\\d+")))
                .andExpect(jsonPath("$.name").value("Nuevo proyecto de prueba"))
                .andExpect(jsonPath("$.ownerId").value(1));
    }

    @Test
    void postProject_nombreEnBlanco_devuelve400ConDetalleDeCampo() throws Exception {
        // @Valid falla ANTES del cuerpo del controller: el detalle por campo del advice menciona "name".
        String body = """
                { "name": "", "description": "sin nombre" }
                """;

        mockMvc.perform(post("/projects")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value(containsString("name")));
    }

    private Task tarea(Long id, String title) {
        try {
            return new Task(id, title, "desc", TaskStatus.TODO, Priority.MED, 1L, 1L, null);
        } catch (TaskValidationException e) {
            throw new IllegalStateException("dato de prueba inválido", e);
        }
    }
}
```

</details>


---

<sub>2m 8s</sub>

### Copilot

Resumen de la revisión contra specs/summary.md.

| # | Gravedad | Archivo:línea | Punto de la checklist | Qué pasa | Qué harías |
|---|---|---:|---|---|---|
| 1 | Sugerencia | src/main/java/com/taskflow/service/ProjectService.java:106 | 3 (Reglas del dominio) | Implementación reutiliza Task.estaVencida() correctamente. | Ninguna acción necesaria; aprobado. |
| 2 | Sugerencia | src/main/java/com/taskflow/mapper/ProjectMapper.java:27 | 4 (Contrato) | byStatus usa Map\<TaskStatus,Long> (EnumMap) — serializa como claves "TODO"/"IN_PROGRESS"/"DONE", OK. | Ninguna acción necesaria; es conforme. |
| 3 | Sugerencia | src/main/java/com/taskflow/service/ProjectService.java:1 | 5 (Convenciones) | Hay imports no usados (p. ej. LocalDate, dto import) en ProjectService. | Limpiar imports (no obligatorio para pasar tests, pero mejora). |
| 4 | Sugerencia | src/test/java/com/taskflow/unit/* (dos clases nuevas) | 7 (Tests que prueban algo) | Hay dos tests unitarios que cubren lo mismo (ProjectServiceTest y ProjectSummaryServiceTest) — ligera duplicación. | Consolidar o mantener; no bloqueante. |

Casos sin test
- Ninguno respecto a la especificación: hay unit tests (con y sin tareas) y slice @WebMvcTest con 200 y 404. (El 401 queda para pruebas de integración/seguridad según convención.)

Veredicto: APROBADO.

---

<sub>Generated by [GitHub Copilot CLI](https://github.com/features/copilot/cli)</sub>
