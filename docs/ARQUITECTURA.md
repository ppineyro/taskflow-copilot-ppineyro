# ARQUITECTURA — Resumen para desarrolladores nuevos

Este documento describe la estructura del proyecto TaskFlow, el recorrido de la petición POST /projects/{projectId}/tasks, dónde viven las reglas de negocio, cómo funciona la seguridad JWT y la organización de tests.

## Capas y paquetes

- Capa HTTP / controladores
  - Archivo principal: `src/main/java/com/taskflow/controller/TaskController.java`
  - Classes: `TaskController`
  - Responsabilidad: recibir solicitudes, validación básica (`@Valid`), y devolver DTOs.

- Capa de servicios (casos de uso)
  - Ruta: `src/main/java/com/taskflow/service/TaskService.java`
  - Classes: `TaskService`, `ProjectService` (consulta existencia de proyecto)
  - Responsabilidad: orquestación de operaciones, transacciones y coordinación entre repositorios y dominio.

- Capa de repositorios (persistencia)
  - Ruta: `src/main/java/com/taskflow/repository/TaskRepository.java`
  - Classes: `TaskRepository`
  - Responsabilidad: acceso a la base de datos (Spring Data JPA).

- Dominio / modelo
  - Ruta: `src/main/java/com/taskflow/model/Task.java`
  - Classes: `Task`, `TaskStatus`, `Priority`
  - Responsabilidad: contener las reglas de negocio y comportamientos (factory `Task.crear`, `Task.estaVencida()`, `setStatus(...)`).

- DTOs y mappers
  - Ruta DTO: `src/main/java/com/taskflow/dto/TaskRequest.java`, `src/main/java/com/taskflow/dto/TaskResponse.java`
  - Mapper: `src/main/java/com/taskflow/mapper/TaskMapper.java` (`TaskMapper`)
  - Responsabilidad: transformar entre DTOs y entidades; distinguir creación (factory) vs rehidratación.

- Config y utilidades
  - Archivo semilla: `src/main/java/com/taskflow/config/DataSeeder.java` (`DataSeeder`)
  - Manejo de excepciones: `src/main/java/com/taskflow/advice/GlobalExceptionHandler.java` (`GlobalExceptionHandler`)
  - Seguridad: paquete `src/main/java/com/taskflow/security/` con `ProjectSecurity` y configuración JWT.

- UI estática
  - `src/main/resources/static` — UI simple servida por la API (login, proyectos, tareas).

## Recorrido completo de POST /projects/{projectId}/tasks

1. Llega la petición HTTP a `TaskController` en `src/main/java/com/taskflow/controller/TaskController.java` (método `createTask`). El cuerpo se mapea a `TaskRequest` y se valida con `@Valid`.

2. `TaskController` valida que el proyecto exista consultando `ProjectService.buscarPorId(projectId)` (si no existe -> `ProjectNotFoundException` -> 404 por el `GlobalExceptionHandler`).

3. Si el proyecto existe, `TaskController` llama a `TaskService.crear(request, projectId)` en `src/main/java/com/taskflow/service/TaskService.java`.

4. Dentro de `TaskService.crear`:
   - Se delega a `TaskMapper.aEntidadNueva(request, projectId)` en `src/main/java/com/taskflow/mapper/TaskMapper.java` para convertir el DTO a entidad.
   - `TaskMapper.aEntidadNueva` usa la factory de dominio `Task.crear(...)` (`src/main/java/com/taskflow/model/Task.java`) para construir la entidad nueva. Aquí se aplican reglas de negocio de creación (p. ej. status inicial `TODO`, `dueDate` no puede estar en el pasado, título obligatorio, etc.).
   - Si la creación viola reglas, `Task.crear` lanza excepciones de validación (`TaskValidationException`) que suben hasta el advice y se traducen a 400/422 según el caso.

5. Si la entidad es válida, `TaskService` persiste con `TaskRepository.save(nueva)` (`src/main/java/com/taskflow/repository/TaskRepository.java`). El repositorio asigna el `id` y devuelve la entidad persistida.

6. `TaskController` construye la respuesta: `201 Created` con cabecera `Location: /tasks/{id}` y el cuerpo `TaskMapper.aResponse(creada)`.

7. Errores y mapeo de excepciones: todas las excepciones conocidas (validación, no encontrado, conflictos, estado inválido) son manejadas por `GlobalExceptionHandler` en `src/main/java/com/taskflow/advice/GlobalExceptionHandler.java` y devuelven códigos HTTP uniformes (400, 404, 409, 422, 401, 403 según corresponda).

## Dónde residen las reglas de negocio

- Reglas invariantes y comportamientos derivados viven en el dominio: `src/main/java/com/taskflow/model/Task.java` (`Task`). Ejemplos:
  - `Task.crear(...)` aplica `status = TODO` y valida `dueDate` no en pasado.
  - `Task.setStatus(...)` aplica reglas para cambiar estado (no pasar a `DONE` sin `assignee`).
  - `Task.estaVencida()` encapsula la lógica de vencimiento.

- La capa de servicio (`TaskService`) orquesta y traduce excepciones de dominio a respuestas adecuadas; no contiene las reglas de negocio principales (las reusa).

- `TaskMapper` conoce la diferencia entre crear (usa factory) y rehidratar (constructor), por eso está en la capa de adaptación.

## Seguridad con JWT

- La implementación de seguridad está en `src/main/java/com/taskflow/security/` (`ProjectSecurity` y clases relacionadas).
- Endpoints públicos: `/auth/**`, `/info`, documentación Swagger y la consola H2 (`src/main/resources/static` mantiene la UI pública mínima).
- El resto de endpoints requieren token JWT en la cabecera `Authorization: Bearer <token>`.
- El filtro/interceptor de seguridad valida la firma del JWT, fecha de expiración y extrae el `username`/roles. Los roles se usan junto a `@PreAuthorize` (p. ej. para acciones de borrado de proyectos solo por el dueño o por `ADMIN`) y a la ayuda de `ProjectSecurity`.
- Si falta/está mal el token -> `401`; si el usuario no tiene permisos -> `403`.

## Organización de tests

- Unit tests (rápidos, sin Spring) en `src/test/java/...unit...` usando JUnit 5 y Mockito. Cubren lógica de clases del dominio (`Task`) y servicios aislados.
- Slice tests: `@WebMvcTest` para controladores y `@DataJpaTest` para repositorios cuando se quiera aislar capa específica.
- Integration tests: `@SpringBootTest` con perfil `test` en `src/test/java/...integration...`. Los `*IT.java` usan Testcontainers y por defecto no se ejecutan con `mvn test` salvo que se habilite `-Ddocker.tests=true`.
- Comandos habituales (PowerShell / Maven):
  - Ejecutar tests normales: `mvn -q test`
  - Ejecutar una sola clase de tests: `mvn -q test "-Dtest=TaskServiceTest"`
  - Ejecutar la aplicación con H2 (datos de ejemplo): `mvn spring-boot:run "-Dspring-boot.run.profiles=h2"`

## Notas prácticas rápidas

- Nunca mover reglas desde `Task` al `TaskService` sin justificarlo: el dominio es la fuente de verdad.
- Controllers devuelven DTOs: usa `TaskMapper` para pasar entidades a `TaskResponse`.
- Crear recursos → responder `201 Created` con `Location` apuntando al endpoint de lectura (p. ej. `/tasks/{id}`).

---

Archivos/clases citados (rápidas referencias):
- `src/main/java/com/taskflow/controller/TaskController.java` (`TaskController`)
- `src/main/java/com/taskflow/service/TaskService.java` (`TaskService`)
- `src/main/java/com/taskflow/mapper/TaskMapper.java` (`TaskMapper`)
- `src/main/java/com/taskflow/model/Task.java` (`Task`)
- `src/main/java/com/taskflow/repository/TaskRepository.java` (`TaskRepository`)
- `src/main/java/com/taskflow/advice/GlobalExceptionHandler.java` (`GlobalExceptionHandler`)
- `src/main/java/com/taskflow/config/DataSeeder.java` (`DataSeeder`)
- `src/main/java/com/taskflow/security/ProjectSecurity.java` (`ProjectSecurity`)
- `src/main/java/com/taskflow/dto/TaskRequest.java` (`TaskRequest`)
- `src/main/java/com/taskflow/dto/TaskResponse.java` (`TaskResponse`)

Si se quiere, se puede añadir un diagrama de secuencia o un ejemplo de request/response para `POST /projects/{projectId}/tasks`.
Las fechas límite se validan en `Task.crear` (`src/main/java/com/taskflow/model/Task.java`).
