# Decisiones de arquitectura (ADR)

Cada archivo es corto: contexto, decisión y consecuencias. Los números siguen el orden en que se tomaron.

- [1. `OrderDelivered` vive en `shared.events`](0001-orderdelivered-en-shared.md)
- [2. `orders` cierra la recepción llamando a `IntakeApi`](0002-recepcion-sincrona.md)
- [3. `orders` libera los seriales llamando a `CatalogApi`](0003-liberar-seriales-por-api.md)
- [4. Ajustes del taller como columnas tipadas](0004-ajustes-del-taller-tipados.md)
- [5. El contenido del historial se guarda como texto canónico JCS](0005-historial-como-texto-canonico.md)
- [6. Índices únicos parciales emulados con columna anulable](0006-indices-unicos-parciales-emulados.md)
- [7. Límite de intentos propio y SBOM con Trivy](0007-limite-de-intentos-propio.md)
- [8. Las invitaciones devuelven el token al dueño](0008-invitacion-sin-correo.md)
- [9. Contrato HTTP en `docs/api.md`; Swagger solo en local](0009-contrato-http-a-mano.md)
- [10. `OWNERSHIP_TRANSFER` lo firma la plataforma](0010-traspaso-firmado-por-la-plataforma.md)
- [11. TOTP obligatorio para el administrador de plataforma](0011-totp-para-el-administrador.md)
- [12. Los pagos de una orden se serializan con bloqueo de fila](0012-pagos-serializados-por-orden.md)
- [13. Las publicaciones incompletas se reintentan](0013-reintento-de-eventos.md)
