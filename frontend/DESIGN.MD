---
version: 1.0
name: Nítida
description: >
  Sistema de diseño de Nítida, SaaS para talleres de motos con historial firmado y verificable.
  Claro por defecto sobre un lino cálido, tinta azul profunda para el texto, índigo como único
  color de acción, menta para lo confirmado y verificado, durazno para lo que requiere atención.
  Tranquilo, serio y cómodo de leer bajo sol o con las manos sucias. Nada de degradados,
  brillos ni adornos: la confianza la dan la claridad, la jerarquía y el sello de verificación.
inspiration:
  - "Linear: tokens desde pocas variables en OKLCH, un solo acento, jerarquía por superficies y bordes finos, tipografía cuidada"
  - "Stripe: tono financiero y de confianza, claro por defecto, tablas y formularios legibles, foco con anillo suave"
  - "Mercury: neutros cálidos, calma, móvil primero"
language: "es-CO, trato de usted"

colors:
  light:
    canvas:            "oklch(0.975 0.007 85)"   # #F9F6F2  lino, fondo de página
    surface:           "oklch(0.995 0.003 85)"   # #FEFDFB  tarjetas, paneles, inputs
    surface-sunken:    "oklch(0.955 0.009 85)"   # #F3F0E9  zonas hundidas, filas alternas, chips neutros
    border:            "oklch(0.905 0.010 85)"   # #E3DFD8  bordes de tarjetas y divisores
    border-strong:     "oklch(0.840 0.012 85)"   # #CECAC2  divisores con énfasis, hover de tarjetas
    border-input:      "oklch(0.660 0.015 85)"   # #969288  borde de inputs, checkbox, radio (3:1)
    ink:               "oklch(0.240 0.030 268)"  # #191F2E  texto principal
    ink-muted:         "oklch(0.500 0.025 268)"  # #5D6372  texto secundario
    ink-subtle:        "oklch(0.640 0.020 268)"  # #878C99  placeholder y deshabilitado, NUNCA texto informativo
    primary:           "oklch(0.500 0.150 268)"  # #3F5BB8  índigo, acción principal, links, foco
    primary-hover:     "oklch(0.450 0.150 268)"  # #324CA7
    primary-active:    "oklch(0.410 0.140 268)"  # #2A4195
    on-primary:        "#FFFFFF"
    primary-soft:      "oklch(0.940 0.030 268)"  # #E2EBFF  fondo de chips "en proceso", selección
    primary-soft-ink:  "oklch(0.400 0.140 268)"  # #283F92  texto sobre primary-soft
    verified:          "oklch(0.520 0.100 168)"  # #137B5E  menta, sello verificado y éxito
    on-verified:       "#FFFFFF"
    verified-soft:     "oklch(0.940 0.035 168)"  # #D6F3E7
    verified-ink:      "oklch(0.450 0.085 168)"  # #11644C
    attention:         "oklch(0.780 0.100 62)"   # #E6A973  durazno, alertas y avisos
    on-attention:      "oklch(0.240 0.030 268)"  # tinta, nunca blanco
    attention-soft:    "oklch(0.950 0.035 62)"   # #FFEAD7
    attention-ink:     "oklch(0.460 0.090 55)"   # #7E4921
    danger:            "oklch(0.530 0.150 27)"   # #B24039  errores y acciones destructivas
    on-danger:         "#FFFFFF"
    danger-soft:       "oklch(0.950 0.025 27)"   # #FFE9E6
    danger-ink:        "oklch(0.460 0.140 27)"   # #972F29
    skeleton:          "oklch(0.930 0.008 85)"   # #EAE7E2
    skeleton-shine:    "oklch(0.965 0.006 85)"   # #F5F3EF
  dark:
    canvas:            "oklch(0.190 0.022 268)"  # #0F131E  noche
    surface:           "oklch(0.235 0.026 268)"  # #191E2B
    surface-sunken:    "oklch(0.165 0.020 268)"  # #0B0E17
    border:            "oklch(0.310 0.025 268)"  # #2B303D
    border-strong:     "oklch(0.400 0.028 268)"  # #414757
    border-input:      "oklch(0.520 0.022 268)"  # #636976
    ink:               "oklch(0.940 0.008 85)"   # #EEEBE5
    ink-muted:         "oklch(0.740 0.018 268)"  # #A6ABB7
    ink-subtle:        "oklch(0.600 0.020 268)"  # #7B808D
    primary:           "oklch(0.740 0.110 268)"  # #8DA8F1
    primary-hover:     "oklch(0.790 0.100 268)"  # #9FB9FC
    primary-active:    "oklch(0.700 0.110 268)"  # #819CE3
    on-primary:        "oklch(0.190 0.022 268)"  # canvas oscuro
    primary-soft:      "oklch(0.300 0.060 268)"  # #212C4C
    primary-soft-ink:  "oklch(0.840 0.080 268)"  # #B4C9FF
    verified:          "oklch(0.800 0.100 168)"  # #78D3B1
    on-verified:       "oklch(0.190 0.022 268)"
    verified-soft:     "oklch(0.290 0.045 168)"  # #113227
    verified-ink:      "oklch(0.850 0.090 168)"  # #91E1C3
    attention:         "oklch(0.800 0.100 62)"   # #ECAF79
    on-attention:      "oklch(0.190 0.022 268)"
    attention-soft:    "oklch(0.300 0.040 62)"   # #3C2918
    attention-ink:     "oklch(0.860 0.080 62)"   # #F8C69B
    danger:            "oklch(0.720 0.130 27)"   # #EB8278
    on-danger:         "oklch(0.190 0.022 268)"
    danger-soft:       "oklch(0.300 0.050 27)"   # #442320
    danger-ink:        "oklch(0.820 0.100 27)"   # #FEABA1
    skeleton:          "oklch(0.270 0.025 268)"  # #212633
    skeleton-shine:    "oklch(0.310 0.026 268)"  # #2B303E

typography:
  families:
    sans: "Inter Variable, ui-sans-serif, system-ui, sans-serif"
    mono: "JetBrains Mono, ui-monospace, SFMono-Regular, Menlo, monospace"
  features: "font-feature-settings: 'cv11', 'ss01'; números con 'tnum' en montos, km y tablas"
  scale:
    display:  { size: 32px, weight: 600, lineHeight: 1.15, letterSpacing: -0.6px }
    title:    { size: 24px, weight: 600, lineHeight: 1.20, letterSpacing: -0.4px }
    heading:  { size: 20px, weight: 600, lineHeight: 1.25, letterSpacing: -0.2px }
    subhead:  { size: 17px, weight: 500, lineHeight: 1.35, letterSpacing: -0.1px }
    body:     { size: 16px, weight: 400, lineHeight: 1.50, letterSpacing: 0 }
    body-sm:  { size: 14px, weight: 400, lineHeight: 1.45, letterSpacing: 0 }
    label:    { size: 14px, weight: 500, lineHeight: 1.30, letterSpacing: 0 }
    caption:  { size: 13px, weight: 400, lineHeight: 1.40, letterSpacing: 0 }
    metric:   { size: 28px, weight: 600, lineHeight: 1.10, letterSpacing: -0.4px, numeric: tabular }
    mono:     { size: 13px, weight: 400, lineHeight: 1.50, letterSpacing: 0 }

spacing: { base: 4px, scale: [4, 8, 12, 16, 20, 24, 32, 40, 48, 64] }

rounded:
  xs: 4px     # chips de estado, kbd
  sm: 6px     # checkbox, inputs pequeños
  md: 10px    # botones, inputs, selects
  lg: 14px    # tarjetas, diálogos, paneles
  xl: 20px    # hoja pública, bottom sheet
  full: 9999px

elevation:
  none: "none"
  card:    "0 1px 2px oklch(0.24 0.03 268 / 0.06)"
  raised:  "0 4px 12px oklch(0.24 0.03 268 / 0.08), 0 1px 3px oklch(0.24 0.03 268 / 0.06)"
  overlay: "0 16px 40px oklch(0.24 0.03 268 / 0.14), 0 2px 8px oklch(0.24 0.03 268 / 0.08)"
  focus:   "0 0 0 3px oklch(0.50 0.15 268 / 0.28)"
  dark-note: "En modo oscuro no hay sombras salvo en overlay; la jerarquía la dan surface y border."

motion:
  fast: 120ms    # hover, press
  base: 180ms    # aparición de popovers, toasts, cambio de estado
  slow: 260ms    # bottom sheet, diálogo
  ease-out: "cubic-bezier(0.2, 0.8, 0.2, 1)"
  ease-in-out: "cubic-bezier(0.4, 0, 0.2, 1)"
  reduced-motion: "Con prefers-reduced-motion se eliminan desplazamientos y el brillo del skeleton; solo se mantienen cambios de opacidad."

breakpoints: { sm: 640px, md: 768px, lg: 1024px, xl: 1280px }
touch-target: { min: 48px, gap: 8px }
---

# Nítida · DESIGN.md

Este documento es la fuente única de verdad visual del frontend. La IA y las personas que escriban UI deben leerlo antes de crear o cambiar una pantalla. Si algo no está aquí, se pregunta antes de inventarlo.

## 1. Principios

1. **Claridad antes que decoración.** Cada píxel tiene que ayudar a entender o a actuar. Si un elemento no cumple ninguna de las dos cosas, se quita.
2. **Calma con carácter.** Neutros cálidos, un índigo profundo y acentos suaves. La pantalla no compite con el trabajo del taller.
3. **Pensado para el taller real.** Celular, sol fuerte, manos con grasa, prisa. Botones grandes, pocos campos por pantalla, contraste alto, una acción principal por pantalla.
4. **La confianza se ve.** El sello de verificación es el único elemento de marca con protagonismo. Todo lo demás se mantiene en segundo plano para que el sello signifique algo.
5. **Un color, un significado.** Índigo es acción. Menta es confirmado o verificado. Durazno es atención. Rojo es error o destrucción. Ningún color se usa como adorno.
6. **Nunca solo color.** Todo estado lleva ícono y texto. Debe entenderse en blanco y negro.
7. **Honestidad.** La interfaz no promete lo que el sistema no garantiza. Un historial "no verificable" no se pinta como válido. Un repuesto escaneado no se presenta como "original garantizado".

## 2. Paleta

### 2.1 Roles

| Rol | Token | Claro | Oscuro | Uso permitido | Prohibido |
|---|---|---|---|---|---|
| Fondo de página | `canvas` | #F9F6F2 | #0F131E | Fondo general | Tarjetas |
| Superficie | `surface` | #FEFDFB | #191E2B | Tarjetas, paneles, inputs, diálogos | Fondo de página |
| Hundido | `surface-sunken` | #F3F0E9 | #0B0E17 | Zonas de agrupación, chips neutros, filas seleccionadas | Botones |
| Texto | `ink` | #191F2E | #EEEBE5 | Títulos, cuerpo, datos | — |
| Texto secundario | `ink-muted` | #5D6372 | #A6ABB7 | Metadatos, ayudas, etiquetas | Datos críticos (montos, placas) |
| Texto tenue | `ink-subtle` | #878C99 | #7B808D | Placeholder, deshabilitado | Cualquier texto que haya que leer para decidir |
| Acción | `primary` | #3F5BB8 | #8DA8F1 | Botón principal, links, foco, selección | Fondos grandes, títulos, decoración |
| Confirmado y verificado | `verified` | #137B5E | #78D3B1 | Sello de verificación, éxito, "Lista para entregar" | Botones de acción, decoración |
| Atención | `attention` | #E6A973 | #ECAF79 | Alertas de fuga (F1–F5), avisos, "no verificable" | Errores, texto blanco encima |
| Error y destrucción | `danger` | #B24039 | #EB8278 | Errores, F6, cadena inválida, botón eliminar o anular | Avisos leves |

Cada color de rol tiene tres variantes: sólido (`verified`), fondo suave (`verified-soft`) y texto sobre el fondo suave (`verified-ink`). Los chips y avisos usan **soft + ink**. Los sólidos se reservan para el botón principal y el sello.

### 2.2 Contrastes verificados (WCAG 2.2)

| Combinación | Claro | Oscuro |
|---|---|---|
| `ink` sobre `canvas` | 15,3:1 | 15,6:1 |
| `ink-muted` sobre `surface` | 5,9:1 | 7,2:1 |
| `on-primary` sobre `primary` | 6,2:1 | 8,0:1 |
| `primary` como texto sobre `canvas` | 5,7:1 | 8,0:1 |
| `on-verified` sobre `verified` | 5,2:1 | 10,4:1 |
| `verified-ink` sobre `verified-soft` | 6,0:1 | 9,1:1 |
| `attention-ink` sobre `attention-soft` | 6,3:1 | 8,9:1 |
| `on-attention` sobre `attention` | 8,1:1 | 9,7:1 |
| `danger-ink` sobre `danger-soft` | 6,6:1 | 7,7:1 |
| `on-danger` sobre `danger` | 5,7:1 | 7,1:1 |
| `border-input` sobre `surface` | 3,05:1 | 3,0:1 |
| `ink-subtle` sobre `surface` | 3,3:1 | 4,2:1 |

`ink-subtle` no cumple 4,5:1 a propósito: solo sirve para placeholder y deshabilitado. Si un texto hay que leerlo, se usa `ink-muted` o `ink`.

### 2.3 Proporción

En cada pantalla, aproximadamente: 70 % neutros (canvas, surface, ink), 20 % texto secundario y bordes, 10 % o menos de color de rol. Si una pantalla se ve "colorida", sobra color.

### 2.4 Mapeo de estados del dominio

| Dominio | Estado | Estilo | Ícono (lucide) | Texto |
|---|---|---|---|---|
| Orden | `OPEN` | neutro (`surface-sunken` + `ink-muted`) | `circle-dashed` | Abierta |
| Orden | `IN_PROGRESS` | `primary-soft` + `primary-soft-ink` | `wrench` | En proceso |
| Orden | `COMPLETED` | `verified-soft` + `verified-ink` | `circle-check` | Lista para entregar |
| Orden | `DELIVERED` | neutro | `package-check` | Entregada |
| Orden | `CANCELLED` | neutro, texto tachado solo en el número | `circle-slash` | Cancelada |
| Evidencia | `MANUAL` | neutro | `pencil` | Manual |
| Evidencia | `SCANNED` | `primary-soft` | `scan-barcode` | Escaneado |
| Evidencia | `UNIQUE_SERIAL` | `primary-soft` | `fingerprint` | Serial único |
| Evidencia | `AUTHENTICITY_VERIFIED` | `verified-soft` | `badge-check` | Sello revisado |
| Verificación | `VALID` | sello `verified` sólido | `shield-check` | Historial verificado |
| Verificación | `INVALID` | `danger-soft` + borde `danger` | `shield-x` | No pudimos comprobar este historial |
| Verificación | `UNVERIFIABLE` | `attention-soft` | `shield-question` | Verificación no disponible |
| Caja | `BALANCED` | `verified-soft` | `scale` | Cuadrada |
| Caja | `UNBALANCED` | `attention-soft` | `scale` | Descuadre de $12.000 |
| Alerta | F1–F5 | `attention-soft` | `triangle-alert` | Texto específico (sección 9.5) |
| Alerta | `F6_DUPLICATE_SERIAL` | `danger-soft` | `octagon-alert` | Serial ya registrado en otra moto |

"Escaneado" nunca se pinta con menta. Escanear es evidencia, no prueba de autenticidad (sección 5.10 de la especificación).

## 3. Tipografía

- **Inter Variable** para todo, autoalojada con `next/font/local`: sin llamadas a Google Fonts, por la CSP. **JetBrains Mono** para hashes, seriales, códigos de reclamo, placas en contexto técnico y el `traceId`.
- Pesos: 400 cuerpo, 500 etiquetas y botones, 600 títulos. Nunca 700 ni 800.
- **Números tabulares** (`font-variant-numeric: tabular-nums`) en montos, kilómetros, fechas en tablas y contadores. Las columnas de dinero se alinean a la derecha.
- Formatos con `Intl` en `es-CO` y zona `America/Bogota`: `$ 125.000`, `18.420 km`, `12 mar 2026`, `12 mar 2026, 3:40 p. m.`.
- Placas siempre en mayúsculas con separador visual: `ABC 12D`. En la API van sin espacio.
- Hashes recortados `a91f…c3d2`, con botón de copiar al lado. Nunca el hash completo en la vista normal.
- Longitud de línea de 45 a 75 caracteres en textos largos (ayudas, términos).
- Mínimo 13 px en cualquier texto. En móvil el cuerpo es 16 px, para que iOS no haga zoom en los inputs.
- Mayúscula inicial solo en la primera palabra: "Nueva orden", no "Nueva Orden". Sin TEXTO EN MAYÚSCULAS salvo placas y siglas.

## 4. Espaciado, radios y elevación

- Escala de 4 px. No se usan valores fuera de la escala (10, 18, 22...).
- Padding de tarjeta: 16 px en móvil, 20–24 px en escritorio. Separación entre tarjetas: 12 px en móvil, 16 px en escritorio.
- Radios: chips 4 px, botones e inputs 10 px, tarjetas 14 px, hoja pública y bottom sheet 20 px. Nada de píldoras en botones de formulario. La píldora (`full`) solo se usa en avatares e indicadores de punto.
- En modo claro, las tarjetas llevan `border` de 1 px más sombra `card` casi imperceptible. En modo oscuro, solo borde. Las sombras `raised` y `overlay` son para menús, popovers, diálogos y bottom sheets. Ninguna tarjeta estática lleva sombra fuerte.

## 5. Layout y responsive

- **Móvil primero (360 px)**. Personal del taller (mecánico y recepción): una columna, acción principal fija abajo en una barra con `surface` y borde superior, y navegación inferior con máximo 4 destinos (Inicio, Recepción, Órdenes, Más).
- **Escritorio (≥ 1024 px), dueño y admin:** barra lateral de 240 px, contenido de máximo 1200 px, tablas en lugar de tarjetas.
- **Hoja pública `/v/[token]`:** una columna centrada de máximo 560 px, sin navegación de la app, pensada para abrirse desde WhatsApp.
- Objetivos táctiles de 48 × 48 px como mínimo, con 8 px entre ellos. El botón principal en móvil mide 52 px de alto y ocupa todo el ancho.
- Pocos campos por pantalla: en recepción y orden, máximo 4 campos visibles antes de la acción. El resto va en pasos o secciones plegables.
- La zona segura inferior (`env(safe-area-inset-bottom)`) se respeta en la barra fija.

## 6. Componentes

Base: **shadcn/ui** sobre Radix con los tokens de la sección 12. Los componentes se adaptan, no se rediseñan desde cero.

### 6.1 Botones

| Variante | Fondo | Texto | Uso |
|---|---|---|---|
| `primary` | `primary` | `on-primary` | Una sola por pantalla: Entregar moto, Guardar, Registrar entrada |
| `secondary` | `surface` + borde `border-strong` | `ink` | Acciones alternativas |
| `ghost` | transparente, hover `surface-sunken` | `ink` | Barras de herramientas, acciones en filas |
| `danger` | `danger` | `on-danger` | Solo dentro del diálogo de confirmación de algo destructivo |
| `link` | — | `primary`, subrayado en hover | Navegación en texto |

- Alto: 40 px en escritorio, 48 px en móvil (52 px el principal fijo). Padding horizontal de 16 px. Radio de 10 px. Texto `label`.
- Íconos de 18 px, a la izquierda del texto y separados 8 px. Botones solo con ícono: 40 o 48 px cuadrados, con `aria-label` y tooltip en escritorio.
- Estados: hover `primary-hover`, presionado `primary-active` y `scale(0.98)`, foco con anillo `focus` de 3 px más 2 px de separación.
- **Cargando:** el botón conserva su ancho, muestra un spinner de 16 px en lugar del ícono y el texto pasa a gerundio ("Guardando…"). Mientras tanto no se puede volver a enviar.
- **Evitar los botones deshabilitados.** Mejor dejarlo activo y, al tocarlo, explicar qué falta junto al campo. Solo se deshabilita si la acción es imposible por permisos, y entonces se explica por qué con una línea visible, no con un tooltip, que no existe en táctil.
- El texto siempre es un verbo concreto: "Entregar moto", "Registrar pago". Nunca "Aceptar", "OK", "Enviar" ni "Continuar" a secas.

### 6.2 Campos de formulario

- Etiqueta siempre visible encima del campo (`label`, `ink`). El placeholder nunca reemplaza a la etiqueta.
- Input: 48 px en móvil y 40 px en escritorio, fondo `surface`, borde `border-input`, radio de 10 px. En foco, borde `primary` más anillo `focus`.
- Texto de ayuda debajo en `caption` + `ink-muted`, solo si aporta algo ("Como aparece en la tarjeta de propiedad").
- Error: borde `danger`, ícono `circle-alert` de 16 px y mensaje en `caption` + `danger-ink` debajo, conectado con `aria-describedby`. El campo se marca con `aria-invalid="true"`.
- Validación: al salir del campo (`blur`) y al enviar, nunca mientras la persona escribe. Al enviar con errores, el foco va al primer campo inválido y arriba aparece un resumen si hay 3 errores o más.
- Teclados correctos: `inputMode="numeric"` para km y montos, `inputMode="email"`, `autoComplete` adecuado, `autoCapitalize="characters"` para placa.
- Montos: se escriben sin separadores y se muestran formateados al salir del campo. El símbolo `$` va fuera del valor editable.
- Requeridos: no se marcan con asterisco rojo. Se marcan los opcionales con "(opcional)" en la etiqueta.

### 6.3 Chips de estado (badge)

Alto de 24 px, padding de 2 px × 8 px, radio de 4 px, `caption` en peso 500, ícono de 14 px más texto. Siempre **soft + ink** del rol (sección 2.4). Nunca un chip sólido, salvo el sello.

### 6.4 Sello de verificación (componente de marca)

El único elemento "llamativo" del sistema. Tiene tres tamaños:

- **Grande** (hoja pública): círculo de 88 px `verified` con `shield-check` de 44 px en `on-verified`, título `title` "Historial verificado" y debajo, en `body-sm` + `ink-muted`, "3 de 3 registros firmados · sin saltos de km".
- **Mediano** (detalle de moto): círculo de 44 px más título `subhead` y una línea de detalle.
- **Pequeño** (listas): chip `verified-soft` con `shield-check` y "Verificado".

Reglas:

- Animación al aparecer: escala de 0,92 a 1 y opacidad de 0 a 1 en 260 ms con `ease-out`, una sola vez. Sin rebote, sin confeti, sin brillo.
- `INVALID` usa el mismo tamaño y posición, pero con `shield-x`, fondo `danger-soft` y el texto "No pudimos comprobar este historial". Debajo, una línea: "Puede que haya sido modificado fuera de Nítida. Pida al taller una copia actualizada." Al público no se le muestra qué registro falló ni datos técnicos. Al dueño del taller, sí: el número de registro y el tipo de fallo.
- `UNVERIFIABLE` usa `shield-question`, fondo `attention-soft` y "Verificación no disponible en este momento". Nunca se presenta como válido. Es fail closed (falla cerrado).
- El sello nunca aparece mientras se verifica. Mientras tanto se muestra el skeleton del sello (sección 7).

### 6.5 Tarjetas y listas

- Tarjeta: `surface`, borde `border`, radio de 14 px, sombra `card` en claro.
- Las listas densas (órdenes, pagos, historial de caja) van como **filas con divisores dentro de una sola tarjeta**, no como una tarjeta por elemento.
- Fila: alto mínimo de 56 px. Título `label` + `ink`, segunda línea `caption` + `ink-muted`, chip o monto a la derecha y `chevron-right` si navega. Toda la fila es clicable, no solo el texto.
- Tarjetas de métrica (tablero): `primary-soft`, `attention-soft` o `surface-sunken` según el significado. Etiqueta en `caption`, número en `metric`. Máximo 4 por fila y 2 en móvil.

### 6.6 Línea de tiempo del historial

Línea vertical de 1 px `border-strong`, punto de 9 px `primary` por registro y `verified` en el último registro verificado. Cada registro muestra tipo de servicio (`label`), fecha · km · taller (`caption` + `ink-muted`) y hash corto en mono. Las enmiendas (`AMENDMENT`) se muestran con sangría bajo el registro que corrigen y la etiqueta "Corrección". El registro original nunca se oculta.

### 6.7 Avisos (banner)

Fondo soft del rol, borde de 1 px del rol con 40 % de opacidad, radio de 10 px, ícono de 20 px, texto `body-sm` + ink del rol. Una acción opcional como link a la derecha. Se usan dentro del contenido, nunca flotando.

### 6.8 Toasts

- Abajo al centro en móvil (sobre la barra fija) y abajo a la derecha en escritorio. Máximo uno visible.
- Duran 4 s, o 6 s si tienen acción. Se pausan con hover o foco.
- Solo para confirmar algo que la persona acaba de hacer ("Pago registrado") o para ofrecer **Deshacer**.
- Los errores que exigen hacer algo **no** van en toast: van junto al campo o en un banner.
- Usan `role="status"`; los errores usan `role="alert"`.

### 6.9 Diálogos y bottom sheets

- Escritorio: diálogo de máximo 480 px de ancho con sombra `overlay` y fondo oscurecido al 45 %. Móvil: bottom sheet con radio de 20 px arriba y asa de 36 × 4 px.
- Solo para confirmar algo **irreversible**: entregar moto (firma el historial), anular pago, cancelar orden. Para lo reversible se ofrece Deshacer en vez de confirmación.
- El título dice qué pasa y el cuerpo, la consecuencia: "¿Entregar la moto ABC 12D?" / "Se firmará el registro en el historial y ya no podrá editarse. Las correcciones se harán como enmienda."
- Botones: acción concreta a la derecha y "Volver" (ghost) a la izquierda. El foco inicial va a "Volver" cuando la acción es destructiva.

### 6.10 Escáner

- Vista de cámara a pantalla completa con un marco guía de 260 × 160 px para códigos de barras o 220 × 220 px para QR, bordes `on-primary` de 2 px y el resto oscurecido.
- Texto arriba en blanco sobre fondo oscurecido: "Apunte al código del repuesto". Abajo: "Escribir código" (secondary) y "Cerrar" (ghost).
- Al leer: vibración de 30 ms, el marco pasa a `verified` durante 200 ms y se cierra con el producto autocompletado.
- QR con URL: se muestra el **dominio en grande** (`title`, mono) y la ruta en `caption`, con el botón secondary "Abrir en el navegador". Nunca se abre solo.
- Sin permiso de cámara: estado vacío con `camera-off`, "Nítida necesita la cámara para escanear", el botón "Escribir código" y una línea sobre cómo dar el permiso en el navegador.

### 6.11 Íconos

**Lucide** (el de shadcn), trazo de 1,75 px, tamaños de 16, 18 y 20 px (24 px máximo para ilustrar un estado vacío). Siempre de contorno, nunca rellenos, salvo el círculo del sello. Los decorativos llevan `aria-hidden`. Prohibidos: `sparkles`, `wand`, `rocket`, `zap` y cualquier ícono de "magia" o "IA".

## 7. Carga y skeletons

### 7.1 Cuándo usar qué

| Situación | Patrón |
|---|---|
| Carga inicial de una pantalla con estructura conocida | Skeleton con la forma exacta del contenido |
| Acción del usuario (guardar, entregar) | Spinner dentro del botón, sin bloquear la pantalla |
| Respuesta en menos de 300 ms | Nada. El skeleton solo aparece si la carga pasa de 300 ms, para evitar parpadeos |
| Carga de más de 8 s | Se mantiene el skeleton y aparece la línea "Esto está tardando más de lo normal" con "Reintentar" |
| Navegación entre pantallas | `loading.tsx` de Next con el skeleton de la pantalla destino |
| Subida de fotos | Miniatura local inmediata con barra de progreso de 2 px `primary` abajo |

Nunca pantallas en blanco ni spinners centrados de página completa.

### 7.2 Reglas del skeleton

- Color `skeleton` con un brillo `skeleton-shine` que recorre de izquierda a derecha en 1,4 s, lineal e infinito. Con `prefers-reduced-motion`, sin brillo: solo una pulsación de opacidad (0,6 ↔ 1) en 1,6 s.
- **Misma geometría que el contenido real**, para que nada salte al cargar: mismos altos de fila, mismos radios, mismo número aproximado de elementos.
- Bloques de texto: alto de 12 px para `caption`, 14 px para `body-sm` y 20 px para `title`, radio de 4 px. Las líneas de párrafo varían de ancho (100 %, 92 %, 64 %), nunca todas iguales.
- Se muestran 3–5 filas en listas. Nunca 20 filas falsas.
- El encabezado de la página, la navegación y los títulos fijos ya se renderizan reales. Solo se esquematizan los datos.
- Accesibilidad: el contenedor lleva `aria-busy="true"` y un texto oculto "Cargando órdenes…". Los bloques del skeleton llevan `aria-hidden`.

### 7.3 Skeletons por pantalla

| Pantalla | Skeleton |
|---|---|
| Tablero | 2–4 tarjetas de métrica (etiqueta de 12 px + número de 28 px) y una tarjeta con 4 filas (línea de 14 px al 40 %, línea de 12 px al 60 % y chip de 24 × 72 px a la derecha) |
| Lista de órdenes | Tarjeta con 5 filas de 56 px y divisores reales |
| Detalle de orden | Título de 20 px al 50 %, chip, 3 bloques de sección de 120 px y barra inferior fija con el botón real deshabilitado visualmente en `skeleton` |
| Historial de moto | Sello mediano en skeleton (círculo de 44 px y 2 líneas) y 3 registros de línea de tiempo con punto `skeleton` |
| Hoja pública | Círculo de 88 px, línea de 24 px al 60 %, línea de 14 px al 45 % y 3 registros |
| Caja | Tarjeta de métrica grande y tabla con 5 filas con la columna de monto alineada a la derecha |

### 7.4 Actualizaciones optimistas

Solo en acciones seguras y reversibles: avanzar el estado de una orden y marcar una tarea. Se actualiza la UI de inmediato y, si el servidor rechaza, se revierte con un banner que explica el motivo. **Nunca** es optimista en: entregar moto, pagos, anulaciones ni nada que firme el historial.

## 8. Estados vacíos

Estructura: ícono de 24 px en `ink-muted` dentro de un círculo de 48 px `surface-sunken`, un título (`subhead`) que **invita a actuar**, una línea de explicación (`body-sm` + `ink-muted`) y un botón primary o secondary.

| Pantalla | Título | Línea | Acción |
|---|---|---|---|
| Órdenes | Aún no hay órdenes | Las órdenes se crean al registrar la entrada de una moto. | Registrar entrada |
| Historial de moto | Esta moto empieza aquí | Su primer servicio en Nítida quedará firmado en este historial. | — |
| Alertas | Todo en orden | No hay movimientos raros en caja ni en órdenes. | — |
| Equipo | Invite a su equipo | Cada persona entra con su propio usuario y permisos. | Invitar persona |
| Búsqueda sin resultados | No encontramos "ABC 12D" | Revise la placa o registre la moto. | Registrar moto |
| Mis motos | Agregue su moto | Use el código que le dio el taller al entregarla. | Ingresar código |

Sin ilustraciones genéricas de personas, cohetes ni cajas vacías. Sin "¡Ups!", "Nada por aquí" ni emojis.

## 9. Errores: qué mostrar y qué nunca mostrar

### 9.1 Nunca se le muestra al usuario

- Stack traces, nombres de clases o paquetes (`co.nitida...`, `NullPointerException`), SQL, nombres de tablas o columnas, mensajes de Hibernate, Spring o PostgreSQL.
- Códigos HTTP sueltos ("Error 500", "403 Forbidden") ni el `code` interno de Problem Details (`VERSION_CONFLICT`). Esos códigos son para el frontend y los logs, no para las personas.
- Textos en inglés del framework o del navegador ("Failed to fetch", "Network Error", "Unexpected token <").
- Valores rotos de JavaScript: `undefined`, `null`, `NaN`, `[object Object]`, `Invalid Date`. Si un dato falta, se muestra "—" o se oculta el campo.
- **Existencia de recursos ajenos.** Una orden, moto o taller que no pertenece al usuario responde igual que una que no existe: "No encontramos esta orden". Nunca "No tiene permiso para ver la orden de otro taller".
- **Enumeración de usuarios.** En el login: "Correo o contraseña incorrectos", sin decir cuál de los dos falló. En recuperar contraseña: "Si el correo está registrado, le enviaremos un enlace", siempre igual.
- Datos de otro taller o de otra moto. En el serial duplicado no se dice en qué moto ni en qué taller está activo.
- Detalles técnicos de la verificación al público: qué hash falló, la clave pública ni el algoritmo. Eso va solo en la vista del dueño y en la documentación técnica.
- Límites exactos de seguridad: "Le quedan 2 intentos" o "Bloqueado por 15 minutos" exactos. Se usa "Demasiados intentos. Espere unos minutos e intente de nuevo."
- Datos personales en URLs, títulos de página, toasts persistentes o mensajes de error (nombre, teléfono, documento del cliente).
- Culpas al usuario o al sistema en tono dramático: "Error fatal", "Algo salió terriblemente mal", "Usted cometió un error".

### 9.2 Qué sí se muestra

1. **Qué pasó**, en palabras del taller.
2. **Qué puede hacer**, con una acción concreta.
3. Si es un error del sistema (5xx), un **código de soporte corto**: los primeros 8 caracteres del `traceId` en mono, con botón de copiar. Ejemplo: "Código de soporte: 7f3a91c2". Sirve para buscar en los logs sin exponer nada interno.

Formato: frase corta y punto. Sin "Error:" al inicio, sin signos de exclamación, sin "por favor" de relleno.

### 9.3 Dónde va cada error

| Tipo | Ubicación |
|---|---|
| Validación de un campo | Debajo del campo |
| Varios campos al enviar | Debajo de cada campo y resumen arriba (si son 3 o más), con foco en el primero |
| Regla de negocio (422, 409) | Banner dentro del formulario o la sección afectada, junto a la acción |
| Sin conexión | Barra delgada arriba, persistente: "Sin conexión. Los cambios se guardarán cuando vuelva la señal." |
| Error del servidor (5xx) | Banner con "Reintentar" y código de soporte. Lo escrito no se pierde |
| Página inexistente o ajena (404) | Página completa con "No encontramos esta página" y un botón a Inicio |
| Sesión vencida (401) | Redirige a login conservando la ruta, con "Su sesión terminó. Ingrese de nuevo para continuar." |
| Taller suspendido (423) | Banner fijo en toda la app, sin acciones de escritura |

**Regla de oro:** un error nunca borra lo que la persona escribió. Los formularios largos (recepción, orden) guardan borrador local en `sessionStorage` sin datos sensibles y lo restauran.

### 9.4 Mensajes por código de la API

El frontend traduce el `code` de Problem Details a un texto fijo. El `title` del backend es solo un respaldo, y nunca se muestra el `detail` si no está en esta lista.

| `code` | Mensaje | Acción |
|---|---|---|
| `VERSION_CONFLICT` | Alguien más cambió esta orden mientras la editaba. | Ver cambios y aplicar los suyos de nuevo |
| `INVALID_TRANSITION` | Esta orden ya no puede pasar a ese estado. | Recargar la orden |
| `INCOMPLETE_PAYMENTS` | Falta registrar $45.000 para poder entregar. | Registrar pago |
| `INVALID_WARRANTY` | Para entregar sin cobro hay que indicar la garantía que cubre el trabajo. | Elegir garantía |
| `MILEAGE_REGRESSION` | El kilometraje es menor que el último registrado (18.420 km). Si cambiaron el tablero, regístrelo como cambio de odómetro. | Registrar cambio de odómetro |
| `INVALID_PRODUCT_CODE` | Este código no parece válido. Revise los números o escríbalo de nuevo. | Escribir código |
| `SERIAL_ALREADY_ACTIVE`* | Este serial ya está registrado en otra moto. Puede guardar el repuesto sin serial. | Guardar sin serial |
| `WORKSHOP_SUSPENDED` | Su taller está suspendido. Puede consultar la información, pero no hacer cambios. | Contactar soporte |
| `RATE_LIMITED`* | Demasiados intentos. Espere unos minutos e intente de nuevo. | — |
| (5xx o desconocido) | No pudimos completar la acción. Lo que escribió sigue aquí. | Reintentar + código de soporte |
| (red caída) | No hay conexión con Nítida. Revise la señal e intente de nuevo. | Reintentar |

\* Códigos que hay que agregar a la especificación si aún no existen.

Los montos y kilómetros de los mensajes vienen del Problem Details (`extensions`) y se formatean en el cliente. Si no vienen, se usa la versión del mensaje sin cifra.

### 9.5 Textos de alertas de fuga (dueño)

| Alerta | Título | Línea |
|---|---|---|
| `F1_INTAKE_WITHOUT_ORDER` | Moto recibida sin orden | La moto ABC 12D entró hace 2 días y no tiene orden de trabajo. |
| `F2_CANCELLED_WITH_PARTS` | Orden cancelada con repuestos | La orden #1039 se canceló después de registrar repuestos. |
| `F3_CASH_MISMATCH` | Descuadre en caja | El cuadre del 17 nov tiene una diferencia de $12.000. |
| `F4_ZERO_TOTAL_WITHOUT_WARRANTY` | Entrega sin cobro | La orden #1040 se entregó en $0 sin garantía registrada. |
| `F5_STALLED_ORDER` | Orden quieta | La orden #1035 lleva 5 días sin movimiento. |
| `F6_DUPLICATE_SERIAL` | Serial repetido | Se intentó usar un serial que ya está activo en otra moto. |

Tono neutro: la alerta describe un hecho, no acusa a nadie. Nunca "Posible robo" ni el nombre del empleado en el título.

## 10. Voz y textos

- **Trato de usted**, cercano y respetuoso, como en un buen taller de Pasto. (Si las pruebas con talleres muestran que prefieren tú, se cambia en todo el producto a la vez.)
- Palabras del oficio: moto, placa, kilometraje, repuesto, orden, entrega, cuadre de caja. No: "activo", "vehículo automotor", "ticket", "workflow".
- Verbos al inicio de los botones. Frases cortas. Sin signos de exclamación en mensajes del sistema.
- Confirmaciones en participio: "Pago registrado", "Moto entregada". Nunca "¡Éxito!" ni "La operación se realizó exitosamente".
- Nunca "simplemente", "solo tiene que", "fácil" ni "rápido". Tampoco "potenciar", "revolucionar", "experiencia", "inteligente", "mágico" ni "sin fricciones".
- Fechas relativas solo hasta 7 días ("hace 2 días"). Después, la fecha completa.
- Placeholders con un ejemplo real: `ABC 12D`, `18420`, `3001234567`. Nunca "Escriba aquí…".

## 11. Qué evitar para que no parezca hecho por IA ni plantilla genérica

### 11.1 Visual

- **Degradados** de cualquier tipo, sobre todo morado a azul o rosa, en fondos, botones, textos o bordes.
- Glassmorphism, `backdrop-blur` decorativo, brillos, resplandores, sombras de colores, textos con degradado o neón.
- Fondos con mallas, ruido, cuadrículas, puntos, círculos difusos ("blobs") o auroras.
- Hero de landing con titular gigante + subtítulo gris + dos botones + captura inclinada en 3D. La landing de Nítida, cuando exista, muestra el producto real y el sello.
- Tarjetas de "features" en cuadrícula de 3 con ícono en un cuadrado de color y texto genérico.
- Emojis en la interfaz, en títulos o como íconos.
- Íconos de chispas, varitas, cohetes, rayos o cerebros.
- Bordes gruesos de color a la izquierda de las tarjetas como adorno.
- Todo redondeado al máximo (24 px o más en tarjetas, botones píldora en formularios).
- Animaciones de entrada en cascada al hacer scroll, contadores que suben, efectos parallax.
- Modo oscuro forzado como "look tech". El modo por defecto es claro.
- Avatares e ilustraciones de stock de personas sonrientes.
- Más de un acento compitiendo en la misma pantalla.

### 11.2 Contenido

- Texto de relleno: lorem ipsum, "Bienvenido a tu dashboard", "Todo lo que necesitas en un solo lugar", "Potencia tu taller".
- Datos de ejemplo falsos y redondos (`$1,000,000`, `John Doe`, `99.9 %`). En demos y capturas: placas, montos y nombres locales plausibles e inventados, con formato `es-CO`.
- Secciones vacías de relleno: testimonios inventados, logos de clientes que no existen, métricas de vanidad ("+10.000 talleres").
- Tooltips que repiten la etiqueta. Ayudas que no ayudan.
- Títulos en Title Case por traducción literal del inglés.

### 11.3 Funcional (cosas innecesarias)

- Funciones que no están en la especificación: chat con IA, asistente, gamificación, insignias, rachas, "modo foco", temas personalizables, selector de colores, onboarding de 6 pasos.
- Gráficos en el tablero sin una decisión detrás. Solo se muestra un número si alguien va a actuar con él.
- Confirmaciones para acciones reversibles. Para esas, Deshacer.
- Carruseles, tabs que ocultan información crítica, menús de hamburguesa en escritorio.
- Splash screens, pantallas de carga de marca, música, sonidos (salvo la vibración del escáner).
- Configuraciones que el usuario no pidió. Un buen valor por defecto vale más que una opción.
- Dependencias de UI extra: no se agregan librerías de animación, de gráficos ni de componentes adicionales sin un ADR.

## 12. Accesibilidad

- WCAG 2.2 nivel AA como mínimo. Los contrastes de la sección 2.2 ya cumplen.
- Foco siempre visible con anillo `focus` de 3 px. Nunca `outline: none` sin reemplazo.
- Navegable completo con teclado. Orden de tabulación igual al visual. Los diálogos atrapan el foco y lo devuelven al cerrar.
- HTML semántico primero (`button`, `a`, `label`, `table`, `nav`, `main`). ARIA solo cuando el HTML no alcanza.
- Las imágenes de fotos de moto llevan `alt` descriptivo ("Tablero de la moto ABC 12D, 18.420 km") o `alt=""` si son decorativas.
- Zoom al 200 % sin pérdida de contenido. Sin bloqueo de zoom en el viewport.
- `lang="es-CO"` en el documento.
- Pruebas: `@axe-core/playwright` en la CI sobre las pantallas principales y revisión manual con lector de pantalla (TalkBack) en recepción y entrega.

## 13. Movimiento

- Solo para dar continuidad o confirmar: aparición de popovers y toasts (180 ms), bottom sheet (260 ms), cambio de chip de estado (120 ms, opacidad) y el sello (260 ms, una vez).
- Siempre `ease-out` al entrar. Sin rebotes, sin `spring` exagerados.
- Nada se anima solo por estar en pantalla ni en bucle, salvo el skeleton y el spinner.
- `prefers-reduced-motion`: solo opacidad, sin desplazamientos ni escala.

## 14. Implementación (Next.js + Tailwind v4 + shadcn/ui)

### 14.1 `app/globals.css`

```css
@import "tailwindcss";
@custom-variant dark (&:where(.dark, .dark *));

:root {
  --canvas: oklch(0.975 0.007 85);
  --surface: oklch(0.995 0.003 85);
  --surface-sunken: oklch(0.955 0.009 85);
  --border: oklch(0.905 0.010 85);
  --border-strong: oklch(0.840 0.012 85);
  --border-input: oklch(0.660 0.015 85);
  --ink: oklch(0.240 0.030 268);
  --ink-muted: oklch(0.500 0.025 268);
  --ink-subtle: oklch(0.640 0.020 268);
  --primary: oklch(0.500 0.150 268);
  --primary-hover: oklch(0.450 0.150 268);
  --primary-active: oklch(0.410 0.140 268);
  --on-primary: oklch(1 0 0);
  --primary-soft: oklch(0.940 0.030 268);
  --primary-soft-ink: oklch(0.400 0.140 268);
  --verified: oklch(0.520 0.100 168);
  --on-verified: oklch(1 0 0);
  --verified-soft: oklch(0.940 0.035 168);
  --verified-ink: oklch(0.450 0.085 168);
  --attention: oklch(0.780 0.100 62);
  --on-attention: oklch(0.240 0.030 268);
  --attention-soft: oklch(0.950 0.035 62);
  --attention-ink: oklch(0.460 0.090 55);
  --danger: oklch(0.530 0.150 27);
  --on-danger: oklch(1 0 0);
  --danger-soft: oklch(0.950 0.025 27);
  --danger-ink: oklch(0.460 0.140 27);
  --skeleton: oklch(0.930 0.008 85);
  --skeleton-shine: oklch(0.965 0.006 85);
  --elev-card: 0 1px 2px oklch(0.24 0.03 268 / 0.06);
  --elev-raised: 0 4px 12px oklch(0.24 0.03 268 / 0.08), 0 1px 3px oklch(0.24 0.03 268 / 0.06);
  --elev-overlay: 0 16px 40px oklch(0.24 0.03 268 / 0.14), 0 2px 8px oklch(0.24 0.03 268 / 0.08);
  --ring: oklch(0.50 0.15 268 / 0.28);

  /* alias shadcn/ui */
  --background: var(--canvas);
  --foreground: var(--ink);
  --card: var(--surface);
  --card-foreground: var(--ink);
  --popover: var(--surface);
  --popover-foreground: var(--ink);
  --primary-foreground: var(--on-primary);
  --secondary: var(--surface-sunken);
  --secondary-foreground: var(--ink);
  --muted: var(--surface-sunken);
  --muted-foreground: var(--ink-muted);
  --accent: var(--surface-sunken);
  --accent-foreground: var(--ink);
  --destructive: var(--danger);
  --input: var(--border-input);
  --radius: 10px;
}

.dark {
  --canvas: oklch(0.190 0.022 268);
  --surface: oklch(0.235 0.026 268);
  --surface-sunken: oklch(0.165 0.020 268);
  --border: oklch(0.310 0.025 268);
  --border-strong: oklch(0.400 0.028 268);
  --border-input: oklch(0.520 0.022 268);
  --ink: oklch(0.940 0.008 85);
  --ink-muted: oklch(0.740 0.018 268);
  --ink-subtle: oklch(0.600 0.020 268);
  --primary: oklch(0.740 0.110 268);
  --primary-hover: oklch(0.790 0.100 268);
  --primary-active: oklch(0.700 0.110 268);
  --on-primary: oklch(0.190 0.022 268);
  --primary-soft: oklch(0.300 0.060 268);
  --primary-soft-ink: oklch(0.840 0.080 268);
  --verified: oklch(0.800 0.100 168);
  --on-verified: oklch(0.190 0.022 268);
  --verified-soft: oklch(0.290 0.045 168);
  --verified-ink: oklch(0.850 0.090 168);
  --attention: oklch(0.800 0.100 62);
  --on-attention: oklch(0.190 0.022 268);
  --attention-soft: oklch(0.300 0.040 62);
  --attention-ink: oklch(0.860 0.080 62);
  --danger: oklch(0.720 0.130 27);
  --on-danger: oklch(0.190 0.022 268);
  --danger-soft: oklch(0.300 0.050 27);
  --danger-ink: oklch(0.820 0.100 27);
  --skeleton: oklch(0.270 0.025 268);
  --skeleton-shine: oklch(0.310 0.026 268);
  --elev-card: none;
  --elev-raised: 0 0 0 1px var(--border);
  --elev-overlay: 0 16px 40px oklch(0 0 0 / 0.45);
  --ring: oklch(0.74 0.11 268 / 0.35);
}

@theme inline {
  --color-canvas: var(--canvas);
  --color-surface: var(--surface);
  --color-surface-sunken: var(--surface-sunken);
  --color-border: var(--border);
  --color-border-strong: var(--border-strong);
  --color-border-input: var(--border-input);
  --color-ink: var(--ink);
  --color-ink-muted: var(--ink-muted);
  --color-ink-subtle: var(--ink-subtle);
  --color-primary: var(--primary);
  --color-primary-hover: var(--primary-hover);
  --color-primary-active: var(--primary-active);
  --color-on-primary: var(--on-primary);
  --color-primary-soft: var(--primary-soft);
  --color-primary-soft-ink: var(--primary-soft-ink);
  --color-verified: var(--verified);
  --color-on-verified: var(--on-verified);
  --color-verified-soft: var(--verified-soft);
  --color-verified-ink: var(--verified-ink);
  --color-attention: var(--attention);
  --color-on-attention: var(--on-attention);
  --color-attention-soft: var(--attention-soft);
  --color-attention-ink: var(--attention-ink);
  --color-danger: var(--danger);
  --color-on-danger: var(--on-danger);
  --color-danger-soft: var(--danger-soft);
  --color-danger-ink: var(--danger-ink);
  --color-skeleton: var(--skeleton);
  --color-skeleton-shine: var(--skeleton-shine);
  --color-background: var(--background);
  --color-foreground: var(--foreground);
  --color-card: var(--card);
  --color-card-foreground: var(--card-foreground);
  --color-popover: var(--popover);
  --color-popover-foreground: var(--popover-foreground);
  --color-primary-foreground: var(--primary-foreground);
  --color-secondary: var(--secondary);
  --color-secondary-foreground: var(--secondary-foreground);
  --color-muted: var(--muted);
  --color-muted-foreground: var(--muted-foreground);
  --color-accent: var(--accent);
  --color-accent-foreground: var(--accent-foreground);
  --color-destructive: var(--destructive);
  --color-input: var(--input);
  --color-ring: var(--ring);

  --font-sans: var(--font-inter), ui-sans-serif, system-ui, sans-serif;
  --font-mono: var(--font-jetbrains), ui-monospace, monospace;

  --radius-xs: 4px;
  --radius-sm: 6px;
  --radius-md: 10px;
  --radius-lg: 14px;
  --radius-xl: 20px;

  --shadow-card: var(--elev-card);
  --shadow-raised: var(--elev-raised);
  --shadow-overlay: var(--elev-overlay);

  --ease-out: cubic-bezier(0.2, 0.8, 0.2, 1);
  --animate-shimmer: shimmer 1.4s linear infinite;
  @keyframes shimmer {
    from { background-position: -200% 0; }
    to { background-position: 200% 0; }
  }
}

@layer base {
  *, ::before, ::after { border-color: var(--border); }
  html { color-scheme: light; }
  html.dark { color-scheme: dark; }
  body {
    background: var(--canvas);
    color: var(--ink);
    font-family: var(--font-sans);
    font-feature-settings: "cv11", "ss01";
    -webkit-font-smoothing: antialiased;
  }
  :focus-visible { outline: none; box-shadow: 0 0 0 2px var(--surface), 0 0 0 5px var(--ring); }
  .tabular { font-variant-numeric: tabular-nums; }
}

@layer components {
  .skeleton {
    border-radius: var(--radius-xs);
    background: linear-gradient(90deg, var(--skeleton) 25%, var(--skeleton-shine) 50%, var(--skeleton) 75%);
    background-size: 200% 100%;
    animation: var(--animate-shimmer);
  }
}

@media (prefers-reduced-motion: reduce) {
  .skeleton { background: var(--skeleton); animation: pulse-opacity 1.6s ease-in-out infinite; }
  @keyframes pulse-opacity { 50% { opacity: 0.6; } }
  *, *::before, *::after { transition-duration: 0.01ms !important; animation-iteration-count: 1 !important; }
  .skeleton { animation-iteration-count: infinite !important; }
}
```

El degradado de `.skeleton` es la **única** excepción a la regla de no usar degradados, porque es funcional (indica carga) y no decorativo.

### 14.2 Organización

```
frontend/src/
├── app/                      # rutas (App Router); cada ruta con loading.tsx (skeleton) y error.tsx
├── components/ui/            # shadcn adaptados a estos tokens (button, input, badge, dialog, sheet, toast...)
├── components/nitida/        # componentes de dominio: VerificationSeal, OrderStatusBadge, EvidenceBadge,
│                             # HistoryTimeline, MetricCard, EmptyState, ErrorBanner, Scanner, SkeletonList
├── lib/errors.ts             # mapa code -> mensaje (sección 9.4) y extracción segura del traceId
├── lib/format.ts             # moneda, km, fechas, placas, hashes (Intl es-CO)
└── styles/globals.css
```

- Las clases de color siempre por token (`bg-surface`, `text-ink-muted`, `bg-verified-soft text-verified-ink`). **Prohibido** usar la paleta por defecto de Tailwind (`bg-blue-500`, `text-gray-600`) o hex sueltos en componentes. Una regla de ESLint o un `grep` en la CI lo hace cumplir.
- Los estados de dominio se mapean en un solo lugar (`OrderStatusBadge`, `EvidenceBadge`, `VerificationSeal`). Las pantallas no eligen colores.
- Modo oscuro con la clase `.dark` (`next-themes`), por defecto `system`, pero la primera vez el personal del taller ve claro.

## 15. Checklist de revisión de UI (PR y salida de la IA)

- [ ] Una sola acción principal (`primary`) por pantalla.
- [ ] Solo tokens. Ningún hex, ningún `blue-500`, ningún degradado (salvo `.skeleton`).
- [ ] Todo estado con ícono y texto, nunca solo color.
- [ ] Menta solo en verificado, confirmado o éxito. "Escaneado" no es menta.
- [ ] Skeleton con la geometría real, retraso de 300 ms y `aria-busy`.
- [ ] Estado vacío con título que invita a actuar y una acción.
- [ ] Errores traducidos con `lib/errors.ts`. Ningún texto técnico, código HTTP ni inglés visible.
- [ ] 404 y "sin permiso" se ven igual. El login no revela si el correo existe.
- [ ] Un error no borra lo escrito.
- [ ] Objetivos táctiles de 48 px o más y textos de 13 px o más. Probado a 360 px de ancho.
- [ ] Foco visible y navegación con teclado. axe sin violaciones.
- [ ] Montos y km con `tabular-nums` y formato `es-CO`.
- [ ] Textos con trato de usted, verbos concretos, sin exclamaciones ni palabras de la lista negra.
- [ ] Nada de la sección 11 (emojis, chispas, glass, blobs, features en cuadrícula, relleno).
- [ ] Probado en claro y oscuro.

## 16. Prompt corto para la IA

> Lee `docs/DESIGN.md` y la sección 12 de `docs/nitida-especificacion.md` antes de escribir UI. Usa solo los tokens de la sección 14 y los componentes de `components/nitida` y `components/ui`. Una acción principal por pantalla. Estados con ícono y texto según la sección 2.4. Cada ruta lleva `loading.tsx` con skeleton de geometría real y `error.tsx` con mensajes de la sección 9. Textos en español de Colombia con trato de usted. No agregues nada de la sección 11 ni librerías nuevas. Al terminar, recorre el checklist de la sección 15 y di cuáles puntos no pudiste cumplir.

## 17. Pendientes

- Logotipo y símbolo de Nítida: aún no existen. El sello no debe confundirse con el logo.
- Validar con 2 o 3 talleres de Pasto: legibilidad bajo sol, trato de usted o tú y tamaño de botones con guantes.
- Agregar `SERIAL_ALREADY_ACTIVE` y `RATE_LIMITED` a la especificación (sección 9 de la API) si no están.
- Revisar la licencia de Inter y JetBrains Mono (ambas OFL al momento de escribir esto) antes de publicar.
- Revisar el color definitivo cuando Pantone anuncie el color de 2027 (diciembre). Si se ajusta, se cambia solo `--primary` y sus derivados en OKLCH (mismo L y C, otro tono).