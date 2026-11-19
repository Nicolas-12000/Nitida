# 9. Contrato HTTP en `docs/api.md`; OpenAPI y Swagger solo en `dev`

**Estado:** aceptada

**Contexto.** springdoc solo documenta rutas funcionales (`RouterFunction`) si cada una se anota con `@RouterOperation`, y expone `/swagger-ui` y `/v3/api-docs`, que en producción deben estar cerrados (A02).

**Decisión.** La referencia para el frontend es `docs/api.md`, escrito a mano y comprobado por `ApiContractTest`, que falla si falta o sobra una ruta. OpenAPI y Swagger UI existen **solo con el perfil `dev`** (`nitida.openapi.enabled`); en producción y en las pruebas están apagados y `OwaspSecurityTest` lo vigila.

**Consecuencias.** Los cuerpos y las respuestas se mantienen a mano en dos lugares (la prosa y el YAML); conviene que una prueba compare ambos con las rutas reales para que no se separen.
