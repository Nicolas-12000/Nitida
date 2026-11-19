# 9. Contrato HTTP en `docs/api.md`; Swagger solo en local

**Estado:** aceptada

**Contexto.** springdoc no infiere las `RouterFunction`. La spec OpenAPI es un archivo. En producción y en el escaneo del CI esa UI no debe estar abierta.

**Decisión.** `docs/api.md` es la guía. `docs/openapi.yaml` es el contrato; el build lo copia al jar. Swagger UI solo con `nitida.openapi.enabled` (perfil `dev`, que `docker compose` activa en local). El job de imagen del CI arranca el mismo compose con `SPRING_PROFILES_ACTIVE=ci`, así el DAST ve la API como en producción: sin Swagger. `OwaspSecurityTest` comprueba que, sin ese flag, `/swagger-ui.html` y `/v3/api-docs` no responden.

**Consecuencias.** Un cambio de ruta se refleja en `docs/api.md` y en `docs/openapi.yaml`. No hay script generador en el repositorio.
