# 7. Límite de intentos propio y SBOM con Trivy

**Estado:** aceptada

**Contexto.** La spec pedía Bucket4j y el plugin CycloneDX. Para una sola instancia, el limitador necesario son 40 líneas, y Trivy ya corre en el CI.

**Decisión.** `RateLimiter` en memoria (ventana deslizante, con limpieza de claves vencidas) y SBOM generado por Trivy sobre la imagen.

**Consecuencias.** El estado es por instancia: con varios nodos hay que moverlo a un almacén compartido (Redis o Bucket4j con JDBC).
