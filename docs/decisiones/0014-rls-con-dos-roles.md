# 14. Row-level security con dos roles de base de datos

**Estado:** aceptada

**Contexto.** La spec (§8.2) pide una segunda barrera entre talleres además del filtro de la aplicación: que PostgreSQL mismo oculte las filas de otro taller, aunque un repositorio olvide filtrar.

**Decisión.**
- **Dos roles.** El *dueño* (`POSTGRES_USER`, el que usa Flyway) crea y cambia el esquema. La aplicación se conecta como `nitida_app`, que no es dueño de nada, no es superusuario y no tiene `BYPASSRLS`, así que las políticas siempre le aplican. El rol lo crea `docker/postgres-init/10-app-role.sh`.
- **Políticas como archivos SQL versionados:** `backend/src/main/resources/db/postgres/V15__row_level_security.sql` (solo PostgreSQL; el perfil de pruebas con H2 no la carga). Una política por tabla de un solo taller (órdenes, pagos, recepciones, clientes, consentimientos, productos, cuadres, alertas y archivos): `workshop_id = app.workshop_id` o, para el planificador de alertas, `app.system = 'on'`.
- **Quién fija el taller.** `TenantSession` es un `TransactionExecutionListener`: al empezar **cada** transacción manda `set_config('app.workshop_id', ..., true)` con el taller de la solicitud (parámetro enlazado, local a la transacción). Así ningún caso de uso puede olvidarlo. Lo que corre sin solicitud lo declara: los listeners con `tenant.use(taller)` y el planificador con `tenant.system()`. La spec decía AOP; el listener de transacciones es el mismo efecto sin proxies ni dependencia nueva.
- **Sin RLS, a propósito** (y probado): `identity.membership`/`invitation` (se leen para decidir quién entra), `workshops.signing_key` (firma desde listeners), `history.history_entry`/`public_link`, `catalog.serial` (globales y sin datos personales: RN-22, RN-94) y `audit.audit_event`.
- **Mínimo privilegio:** `nitida_app` no puede modificar ni borrar `audit.audit_event` ni `history.history_entry`. «Solo se agrega» deja de ser una convención y pasa a ser un permiso.

**Consecuencias.** Quien ejecute SQL a mano como `nitida_app` sin elegir taller no ve filas de esas tablas. Una base ya creada con un solo usuario necesita `docker compose down -v` una vez para provisionar el rol. Un caso de uso nuevo que corra sin solicitud debe llamar a `tenant.use` o `tenant.system`, o verá cero filas. `RlsIntegrationTest` (PostgreSQL real con Testcontainers) vigila todo esto y falla si aparece una tabla con `workshop_id` sin política y sin estar en la lista documentada.
