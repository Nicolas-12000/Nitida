# Nítida · frontend

Next.js 16 (App Router, Cache Components) + Tailwind v4, construido sobre [`DESIGN.MD`](DESIGN.MD) y el contrato [`../docs/api.md`](../docs/api.md).

## Arranque

```bash
corepack pnpm install
corepack pnpm dev        # http://localhost:3000
```

El navegador siempre llama al mismo origen (`/api/v1/...`) y Next lo reenvía al backend Spring (`NITIDA_API_URL`, por defecto `http://localhost:8080`). Para usarlo con el backend real, levante primero `docker compose up` en la raíz.

### Modo demostración

Sin backend, la pantalla de ingreso ofrece **Probar sin servidor**: un backend simulado en el navegador (`src/lib/demo/`) con un taller de ejemplo de Pasto y las mismas formas de respuesta, códigos de error y reglas principales que la API real (transiciones de orden, pagos, entrega con código de reclamo, cadena SHA-256 del historial, caja, alertas F1–F6, portal del propietario). Los datos viven en `localStorage` de ese navegador y se reinician desde la barra superior. Se apaga con `NEXT_PUBLIC_NITIDA_DEMO=off`.

| Rol de ejemplo | Qué recorrer |
|---|---|
| Dueño del taller | Tablero, kanban de órdenes, caja y alertas, equipo, ajustes |
| Recepción | Registrar entrada (con fotos de ejemplo), cobrar y entregar |
| Mecánico | Trabajos, repuestos con escáner y evidencia; no ve pagos |
| Propietaria de moto | Mis motos, historial, enlace público y traspaso |
| Administración | Activar TOTP (obligatorio) y luego verificar o suspender talleres |

### Cuenta y seguridad (contrato desde la versión con TOTP)

- **Ingreso con TOTP:** un `401 TOTP_REQUIRED` muestra el paso del código de 6 dígitos; el mismo campo acepta un código de recuperación.
- **Mi cuenta** (`/cuenta`): activar la verificación en dos pasos (QR con la librería `qrcode`, primer código y 8 códigos de recuperación que se muestran una vez) y unirse a un taller pegando el código de invitación.
- **Administrador sin TOTP** (`totpSetupRequired`): `/admin` lo lleva a activarlo primero.
- **Recuperar contraseña** (`/recuperar`): el código llega por correo (Mailpit en desarrollo, bandeja en http://localhost:8025).
- **Invitaciones:** el código va por correo; el dueño ya no lo ve.
- **Textos públicos** (diagnóstico, trabajos, nombre de repuesto, motivos): avisan mientras se escribe si llevan un correo, un teléfono o una cédula (`PERSONAL_DATA`, RN-22).

En el modo demostración no hay correo: un *buzón de demostración* muestra el código que habría llegado, y la pantalla de TOTP muestra el código vigente para probar sin aplicación.

## Estructura

```
src/app/            rutas: portada, /login, /registro, /v/[token] (hoja pública) y (app)/ para el taller
src/components/     ui/ (botones, campos, diálogos), nitida/ (estados, sello, línea de tiempo, escáner),
                    brand/ (logo), illustrations/ (registro, eslabón, tacómetro, sello), shell/, landing/
src/lib/            api.ts (CSRF, X-Workshop-Id), errors.ts (code → mensaje), format.ts (es-CO),
                    session.tsx, use-query.ts, chain.ts, demo/ (backend simulado)
```

## Comandos

```bash
corepack pnpm lint
npx tsc --noEmit
corepack pnpm build
```
