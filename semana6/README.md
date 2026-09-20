# Proyecto final · Semana 6 · GitHub Copilot

**Alumno:** `Pablo Enrique Senés Piñeyro` · **Usuario de GitHub:** `ppineyro`

## 1. Qué construí

| | Feature | Especificación |
|---|---|---|
| [-] | `GET /tasks/search?q=` — buscar tareas por título | [`specs/search.md`](../specs/search.md) |
| [-] | `PATCH /tasks/{id}/assignee` — cambiar el responsable | [`specs/assignee.md`](../specs/assignee.md) |
| [x] | `GET /reports/progress` — avance por proyecto | [`specs/progress.md`](../specs/progress.md) |

## 2. El pull request

- **URL del PR (mergeado):** `https://github.com/ppineyro/taskflow-copilot-ppineyro/pull/5`
- **Commit del merge en `main`:** `e6efe05 (HEAD -> main, origin/main, origin/HEAD) Merge pull request #5 from ppineyro/feature/assignee`
- **Comentarios de Copilot code review:** `8`

## 3. Cómo lo hice

| Paso | Qué hice | Evidencia |
|---|---|---|
| Rama y spec | `git switch -c feature/assignee` y copié la spec a `specs/` | `git log --oneline main..feature/assignee` (antes del merge) |
| Implementación | `copilot -p "/crear-endpoint-taskflow …"` con `gpt-5-mini` | `semana6/sesion-implementacion.md` (tiene la línea `Skill "crear-endpoint-taskflow" loaded successfully`) |
| Revisión | agente `revisor` sobre `semana6/proyecto-final.diff` | `semana6/revision.md` (termina con `Veredicto:`) |
| Tests | `mvn test` en verde | `[INFO] Tests run: 86, Failures: 0, Errors: 0, Skipped: 0` |
| Comprobación REST | `verificar.ps1` con `casos-assignee.ps1` | sección 5 de este documento |
| Code review | Copilot en el PR | la pestaña *Files changed* del PR |

## 4. Qué hizo el agente y qué corregí yo

| # | Qué hizo mal el agente (archivo) | Quién lo detectó | Cómo quedó corregido |
|---|---|---|---|
| 1 | Usó paginación simple y no procesa respuestas paginadas de AWS (no usa NextToken) - `.github/skills/limpieza-aws/auditoria.py` | Copilot review | `prompt de corrección, y commit` |
| 2 | Deshabilitó la ejecución de JaCoCo por defecto en mvn verify - `pom.xml` | Copilot review | `prompt de corrección, y commit` |
| 3 | Omitió los casos adicionales de reasignación en la descripción del paso 4 - `.github/skills/verificar-taskflow/verificar.ps1` | Copilot review | `prompt de corrección, y commit` |
| 4 | Dio un veredicto de CUENTA LIMPIA sin el respaldo de la evidencia de la auditoría - `evidencia/dia4/aws-resultado.txt` | Copilot review | `prompt de corrección, y commit` |
| 5 | Falta documentar el endpoint PATCH /tasks/{id}/assignee en OpenAPI - `src/main/java/com/taskflow/controller/TaskController.java` | Copilot review | `prompt de corrección, y commit` |
| 6 | Usó @MockBean en lugar de la convención @MockitoBean - `src/test/java/com/taskflow/slice/ReasignarTareaControllerTest.java` | Copilot review | `prompt de corrección, y commit` |
| 7 | ProjectServiceTest duplica cobertura existente de ProjectSummaryServiceTest.resumen - `src/test/java/com/taskflow/unit/ProjectSummaryServiceTest.java` | Copilot review | `prompt de corrección, y commit` |
| 8 | Mockito openMocks no se cierra en las pruebas en lugar de usar @ExtendWith - `src/test/java/com/taskflow/unit/ReasignarTareaServiceTest.java` | Copilot review | `prompt de corrección, y commit` |

**Lo que el agente hizo bien a la primera** (una o dos líneas): `La lógica principal del feature, pero se tuvo que tocar básicamente todo lo que eran los detalles`

## 5. Comprobaciones REST

Repositorio: F:\eAcademyMty\taskflow-copilot-ppineyro
URL de la app: http://127.0.0.1:8080
Empaquetando con Maven (mvn -q package -DskipTests), tarda unos segundos...
App arrancando (PID 20676). Esperando a que /info responda...
App lista en 9 s.
[OK]    GET /tasks/overdue devuelve solo la tarea 7
[OK]    GET /tasks/unassigned devuelve las tareas 4 y 6
[OK]    GET /projects/1/summary
[OK]    GET /projects/2/summary
[OK]    GET /projects/3/summary
[OK]    GET /projects/99/summary responde 404
[OK]    GET /projects/1/summary sin token responde 401
[OK]    PATCH /tasks/4/assignee asigna a luis
[OK]    GET /tasks/4 conserva el responsable nuevo
[OK]    PATCH /tasks/4/status a DONE ahora responde 200
[OK]    PATCH /tasks/2/assignee (DONE) responde 422
[OK]    PATCH /tasks/99/assignee responde 404
[OK]    PATCH /tasks/6/assignee con {} responde 400 y nombra assigneeId
[OK]    PATCH /tasks/6/assignee con 0 responde 400
[OK]    PATCH /tasks/6/assignee sin token responde 401
App detenida (PID 20676).
[OK]    App apagada: el puerto 8080 ya no responde
RESULTADO: 16/16 OK

## 6. Créditos de la semana

| Qué | AI credits |
|---|---|
| Usados en septiembre según github.com (incluye semanas anteriores si usaste Copilot antes) | `212.71` |
| Implementación con la skill (`AI Credits` del PF-2) | `4.24` |
| Revisión del `revisor` (`AI Credits` del PF-3) | `9.82` |
| Correcciones del PF-4 y del PF-6, si hubo (`AI Credits`) | `12.65` |
