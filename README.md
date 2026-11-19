# Nítida

SaaS para talleres de motos: recepción, órdenes de trabajo, pagos, caja y un **historial de cada moto firmado y verificable** por un link público. Proyecto final de *Pruebas y Seguridad*.

- `backend/` — Spring Boot 4 (Java 21), monolito modular con Spring Modulith. Lo que se evalúa aquí.
- `frontend/` — Next.js; lo desarrolla otra persona contra [`docs/api.md`](docs/api.md).
- [`Nitida-especificacion.md`](Nitida-especificacion.md) — la spec: manda en negocio, seguridad y API.
- [`backend/planmvp.md`](backend/planmvp.md) — estado del MVP contra la spec y lo que falta.

## Arranque

Requisitos: JDK 21 y Docker (solo para `docker compose`).

```bash
cp .env.example .env        # y completa los tres valores (abajo)
docker compose up           # postgres, backend (:8080) y mailpit (SMTP :1025, bandeja :8025)
```

| Variable | Qué es |
|---|---|
| `POSTGRES_PASSWORD` | Clave de la base. Obligatoria: `docker compose` no arranca sin ella |
| `NITIDA_SIGNING_KEK` | Llave de 32 bytes en base64 que cifra las llaves de firma de los talleres. **No va al repositorio.** Generar con `openssl rand -base64 32` |
| `NITIDA_APP_DB_PASSWORD` | Clave del rol `nitida_app`, con el que se conecta la aplicación (no es dueño de las tablas, así que aplica el row-level security). Distinta de `POSTGRES_PASSWORD`, que es del dueño y solo usa Flyway |
| `NITIDA_COOKIE_SECURE` | `true` (por defecto). Solo `false` en desarrollo local por HTTP |
| `NITIDA_DEV_SEED_PASSWORD` | Opcional, solo perfil `dev` (12+ caracteres). Si se define, al arrancar crea datos de desarrollo: ver abajo |

Sin Docker, las pruebas usan H2 en modo PostgreSQL: no necesitan nada instalado. Las de row-level security (`RlsIntegrationTest`) necesitan Docker (Testcontainers) y se omiten si no hay.

Si ya tenías una base creada con una versión anterior, ejecuta `docker compose down -v` una vez: el rol `nitida_app` se crea solo cuando se crea la base.

### Datos de desarrollo (para el frontend)

Con `NITIDA_DEV_SEED_PASSWORD` definida y el perfil `dev` (el de `docker compose` por defecto), el primer arranque crea por la propia API: 1 administrador, 2 talleres (uno verificado, uno pendiente) y 5 motos entregadas con historial firmado. Todas las cuentas usan esa clave:

| Cuenta | Qué es |
|---|---|
| `admin@nitida.test` | Administrador de la plataforma |
| `taller.verificado@nitida.test` | Dueño de «Taller El Rayo» (verificado, con las 5 motos `DEV01A`…`DEV05A`) |
| `taller.pendiente@nitida.test` | Dueña de «Taller La Chispa» (pendiente de verificación) |

El administrador necesita TOTP en producción (spec §10); en el perfil `dev` esa exigencia está apagada (`nitida.admin.totp-required=false`) para explorar sin aplicación de autenticación. No hay clave por defecto: sin la variable no se crea nada. Si ya existe `admin@nitida.test`, no vuelve a sembrar.

## Comandos

Desde `backend/`:

```bash
./gradlew build      # compila, corre las pruebas, ArchUnit, Spring Modulith verify y la regla de cobertura JaCoCo
./gradlew pitest     # pruebas de mutación del dominio (falla bajo el umbral del build)
./gradlew test --tests 'co.nitida.security.*'   # reglas ArchUnit de seguridad, matriz de acceso y pruebas OWASP
```

Contrato HTTP: [`docs/api.md`](docs/api.md) (prosa) y [`docs/openapi.yaml`](docs/openapi.yaml) (OpenAPI 3). `docker compose up` activa el perfil `dev` y abre Swagger UI en http://localhost:8080/swagger-ui.html. Sin ese perfil (pruebas, CI, un arranque normal) la UI y `/openapi.yaml` responden cerrado. El YAML no se genera: se edita en `docs/` y el build lo empaqueta.

Los informes quedan en `backend/build/reports/` (`tests`, `jacoco`, `pitest`).

## Calidad y seguridad

| Qué | Dónde |
|---|---|
| Mapa OWASP Top 10:2025 → control → prueba → archivo | [`docs/owasp-top10.md`](docs/owasp-top10.md) |
| Modelo de amenazas (STRIDE) | [`docs/amenazas.md`](docs/amenazas.md) |
| Decisiones de arquitectura | [`docs/decisiones/`](docs/decisiones/README.md) |
| Diagramas de módulos (los regenera `ArchitectureTest`) | [`docs/modulos/`](docs/modulos) |
| Contrato HTTP para el frontend | [`docs/api.md`](docs/api.md) |

### SonarQube

El CI analiza con SonarQube cuando existen el secret `SONAR_TOKEN` y la variable `SONAR_HOST_URL` en GitHub (si faltan, el paso se omite sin fallar) y espera el Quality Gate. Para correrlo localmente:

```bash
docker run -d -p 9000:9000 sonarqube:community      # luego crea un token en http://localhost:9000
cd backend && ./gradlew build
sonar-scanner -Dsonar.host.url=http://localhost:9000 -Dsonar.token=<token>   # desde la raíz; usa sonar-project.properties
```

### Pipeline (`.github/workflows/ci.yml`)

Detecta qué cambió y solo corre lo necesario, en paralelo. Siempre: gitleaks y Trivy sobre el repositorio (críticas y altas corregibles, incluido el frontend). Con cambios en el backend: compilación y pruebas (más Sonar), mutación con PIT, CodeQL Java, imagen (Trivy sobre lo que se despliega, solo críticas corregibles; ZAP pasivo sobre el OpenAPI, con el proxy en el 8090) y SBOM en `main`. Con cambios en el frontend: tipos, lint y CodeQL JavaScript. Un barrido semanal de CodeQL corre aunque no haya commits. El check que se exige en la rama es **`CI passed`**, e incluye CodeQL.

## Lo que la firma del historial garantiza (y lo que no)

Cada entrada del historial se encadena con la anterior (SHA-256 sobre JSON canónico RFC 8785) y se firma con Ed25519 con la llave de su taller. Eso prueba **integridad** y **qué cuenta de taller registró qué y cuándo dentro de la plataforma**: cualquier alteración o borrado posterior se detecta al verificar.

**No** es no-repudio fuerte frente a la plataforma: el servidor guarda las llaves privadas (cifradas con la KEK), así que quien controle el servidor, la base y la KEK podría reescribir una cadena completa. La mitigación prevista, fuera del MVP, es publicar periódicamente las cabezas de cadena en un registro externo.

## Límites conocidos

Ver el checklist de [`backend/planmvp.md`](backend/planmvp.md). El límite de intentos es en memoria (una instancia). Las invitaciones y la recuperación de contraseña salen por Mailpit. Para rotar la KEK, la nueva queda en `NITIDA_SIGNING_KEK` (y se sube `NITIDA_SIGNING_KEK_VERSION`) y la anterior en `NITIDA_SIGNING_KEK_PREVIOUS` con su versión: las llaves ya selladas siguen abriendo.
