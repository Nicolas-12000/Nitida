"use client";
// Kit de figuras (DESIGN.MD §19): registro, eslabón, arco de tacómetro y sello. Solo tokens, sin texto dentro,
// animadas una sola vez al entrar en pantalla y completas de entrada con movimiento reducido.
import { motion, useReducedMotion, type Transition } from "motion/react";

const EASE: Transition["ease"] = [0.2, 0.8, 0.2, 1];

function useMotion() {
  const reduce = useReducedMotion();
  return {
    reduce,
    once: { once: true, amount: 0.4 } as const,
  };
}

/** Tarjeta de registro abstracta: punto índigo y barras de texto. */
function Record({ x, y, w = 132, h = 64, verified = false, delay = 0 }: { x: number; y: number; w?: number; h?: number; verified?: boolean; delay?: number }) {
  const { reduce, once } = useMotion();
  return (
    <motion.g
      initial={reduce ? false : { opacity: 0, y: 8 }}
      whileInView={{ opacity: 1, y: 0 }}
      viewport={once}
      transition={{ duration: 0.26, delay, ease: EASE }}
    >
      <rect x={x} y={y} width={w} height={h} rx="14" fill="var(--surface)" stroke="var(--border-strong)" strokeWidth="1.25" />
      <circle cx={x + 18} cy={y + 20} r="4.5" fill={verified ? "var(--verified)" : "var(--primary)"} />
      <rect x={x + 30} y={y + 16} width={w * 0.45} height="8" rx="4" fill="var(--ink-muted)" opacity="0.55" />
      <rect x={x + 14} y={y + 34} width={w * 0.72} height="6" rx="3" fill="var(--border-strong)" />
      <rect x={x + 14} y={y + 46} width={w * 0.5} height="6" rx="3" fill="var(--border-strong)" />
    </motion.g>
  );
}

/** Eslabón: dos píldoras entrelazadas de trazo 2,25. */
function Link({ x, y, angle = 0, delay = 0 }: { x: number; y: number; angle?: number; delay?: number }) {
  const { reduce, once } = useMotion();
  const common = {
    fill: "none", stroke: "var(--primary)", strokeWidth: 2.25, strokeLinecap: "round" as const,
    initial: reduce ? false : { pathLength: 0 }, whileInView: { pathLength: 1 }, viewport: once,
    transition: { duration: 0.24, delay, ease: EASE },
  };
  return (
    <g transform={`translate(${x} ${y}) rotate(${angle})`}>
      <motion.rect x={-17} y={-6} width="22" height="12" rx="6" {...common} />
      <motion.rect x={-5} y={-6} width="22" height="12" rx="6" {...common} transition={{ ...common.transition, delay: delay + 0.08 }} />
    </g>
  );
}

export function SealFigure({ cx, cy, r = 26, delay = 0, tone = "verified" }: { cx: number; cy: number; r?: number; delay?: number; tone?: "verified" | "danger" | "attention" }) {
  const { reduce, once } = useMotion();
  const fill = { verified: "var(--verified)", danger: "var(--danger)", attention: "var(--attention)" }[tone];
  const ink = { verified: "var(--on-verified)", danger: "var(--on-danger)", attention: "var(--on-attention)" }[tone];
  const s = r / 26;
  return (
    <motion.g
      style={{ transformOrigin: `${cx}px ${cy}px` }}
      initial={reduce ? false : { opacity: 0, scale: 0.92 }}
      whileInView={{ opacity: 1, scale: 1 }}
      viewport={once}
      transition={{ duration: 0.26, delay, ease: EASE }}
    >
      <circle cx={cx} cy={cy} r={r + 8 * s} fill={fill} opacity="0.28" />
      <circle cx={cx} cy={cy} r={r} fill={fill} />
      {tone === "verified" ? (
        <motion.path
          d={`M${cx - 10 * s} ${cy + 1 * s} l${7 * s} ${7 * s} l${13 * s} ${-14 * s}`}
          fill="none" stroke={ink} strokeWidth={4 * s} strokeLinecap="round" strokeLinejoin="round"
          initial={reduce ? false : { pathLength: 0 }} whileInView={{ pathLength: 1 }} viewport={once}
          transition={{ duration: 0.22, delay: delay + 0.26, ease: EASE }}
        />
      ) : tone === "danger" ? (
        <path d={`M${cx - 8 * s} ${cy - 8 * s} l${16 * s} ${16 * s} M${cx + 8 * s} ${cy - 8 * s} l${-16 * s} ${16 * s}`} stroke={ink} strokeWidth={4 * s} strokeLinecap="round" />
      ) : (
        <path d={`M${cx} ${cy - 9 * s} v${10 * s} M${cx} ${cy + 8 * s} v0.5`} stroke={ink} strokeWidth={4 * s} strokeLinecap="round" />
      )}
    </motion.g>
  );
}

/** Cabecera de marca 480 × 220: registros encadenados que terminan en el sello. */
export function ChainHero({ className = "", title = "Tres registros encadenados que terminan en un sello verificado" }: { className?: string; title?: string }) {
  return (
    <svg viewBox="0 0 480 220" className={className} role="img" aria-label={title}>
      <title>{title}</title>
      <Record x={12} y={128} delay={0} />
      <Link x={162} y={142} angle={-18} delay={0.34} />
      <Record x={174} y={78} delay={0.08} />
      <Link x={324} y={92} angle={-18} delay={0.5} />
      <Record x={336} y={28} w={118} delay={0.16} verified />
      <SealFigure cx={432} cy={128} r={24} delay={0.7} />
    </svg>
  );
}

/** Arco de tacómetro de 116° con marcas cada 6,4°; una de cada tres más larga, las últimas en índigo. */
export function Tachometer({ size = 96, level = 1, tone = "primary", className = "" }: { size?: number; level?: number; tone?: "primary" | "verified" | "muted"; className?: string }) {
  const { reduce, once } = useMotion();
  const ticks = 19;
  const cx = 48, cy = 58, R = 38;
  const active = Math.round(level * (ticks - 1));
  const color = { primary: "var(--primary)", verified: "var(--verified)", muted: "var(--ink-muted)" }[tone];
  return (
    <svg width={size} height={size} viewBox="0 0 96 96" className={className} aria-hidden>
      {Array.from({ length: ticks }, (_, i) => {
        const deg = -148 + i * 6.4 * (116 / 115.2);
        const rad = (deg * Math.PI) / 180;
        const long = i % 3 === 0;
        const r1 = R - (long ? 11 : 6);
        // Las últimas marcas antes de la aguja van en el color de acento; las recorridas en tinta suave.
        const on = i <= active && i > active - 4;
        return (
          <motion.line
            key={i}
            x1={cx + Math.cos(rad) * r1}
            y1={cy + Math.sin(rad) * r1}
            x2={cx + Math.cos(rad) * R}
            y2={cy + Math.sin(rad) * R}
            stroke={on ? color : i <= active ? "var(--ink-muted)" : "var(--border-strong)"}
            strokeWidth={long ? 3 : 2}
            strokeLinecap="round"
            initial={reduce ? false : { opacity: 0 }}
            whileInView={{ opacity: 1 }}
            viewport={once}
            transition={{ duration: 0.12, delay: on ? 0.42 + (i - active + 4) * 0.04 : i * 0.02 }}
          />
        );
      })}
      <motion.line
        x1={cx}
        y1={cy}
        x2={cx + Math.cos(((-148 + active * 6.4) * Math.PI) / 180) * (R - 16)}
        y2={cy + Math.sin(((-148 + active * 6.4) * Math.PI) / 180) * (R - 16)}
        stroke="var(--ink)"
        strokeWidth="3"
        strokeLinecap="round"
        initial={reduce ? false : { opacity: 0 }}
        whileInView={{ opacity: 1 }}
        viewport={once}
        transition={{ duration: 0.18, delay: 0.5 }}
      />
      <circle cx={cx} cy={cy} r="5" fill="var(--surface)" stroke="var(--ink)" strokeWidth="3" />
    </svg>
  );
}

/** Estado vacío de órdenes: un registro con un eslabón abierto esperando el siguiente. */
export function EmptyOrdersFigure() {
  return (
    <svg width="96" height="96" viewBox="0 0 96 96" aria-hidden>
      <rect x="10" y="22" width="60" height="52" rx="14" fill="var(--surface)" stroke="var(--border-strong)" strokeWidth="1.5" />
      <circle cx="24" cy="38" r="4" fill="var(--primary)" />
      <rect x="32" y="35" width="26" height="6" rx="3" fill="var(--border-strong)" />
      <rect x="20" y="50" width="40" height="5" rx="2.5" fill="var(--border)" />
      <rect x="20" y="60" width="28" height="5" rx="2.5" fill="var(--border)" />
      <rect x="66" y="42" width="22" height="12" rx="6" fill="none" stroke="var(--primary)" strokeWidth="2.25" strokeDasharray="4 4" />
    </svg>
  );
}

/** Moto sin historial: un solo registro con su punto índigo. */
export function EmptyHistoryFigure() {
  return (
    <svg width="96" height="96" viewBox="0 0 96 96" aria-hidden>
      <line x1="24" y1="10" x2="24" y2="86" stroke="var(--border-strong)" strokeWidth="1.5" strokeDasharray="3 5" />
      <circle cx="24" cy="48" r="6" fill="var(--primary)" />
      <rect x="38" y="30" width="48" height="36" rx="10" fill="var(--surface)" stroke="var(--border-strong)" strokeWidth="1.5" />
      <rect x="46" y="40" width="26" height="6" rx="3" fill="var(--ink-muted)" opacity="0.5" />
      <rect x="46" y="52" width="32" height="5" rx="2.5" fill="var(--border)" />
    </svg>
  );
}

/** Eslabón roto para el 404. */
export function BrokenLinkFigure() {
  return (
    <svg width="96" height="96" viewBox="0 0 96 96" aria-hidden>
      <rect x="10" y="40" width="34" height="18" rx="9" fill="none" stroke="var(--border-strong)" strokeWidth="3" transform="rotate(-14 27 49)" />
      <rect x="52" y="38" width="34" height="18" rx="9" fill="none" stroke="var(--primary)" strokeWidth="3" transform="rotate(14 69 47)" />
      <path d="M46 30 l2 8 M38 32 l5 6 M54 32 l-3 7" stroke="var(--attention)" strokeWidth="2.25" strokeLinecap="round" />
    </svg>
  );
}
