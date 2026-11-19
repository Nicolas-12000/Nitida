"use client";
import { motion, useReducedMotion } from "motion/react";

/**
 * Símbolo de Nítida (DESIGN.MD §17): N de un solo trazo continuo en retícula de 64, trazo 7,5,
 * astas en x = 19,5 y 44,5, y el punto menta del último registro firmado en (44,5; 19).
 */
export function Mark({ size = 32, animate = false, tile = true }: { size?: number; animate?: boolean; tile?: boolean }) {
  const reduce = useReducedMotion();
  const draw = animate && !reduce;
  return (
    <svg width={size} height={size} viewBox="0 0 64 64" role="img" aria-label="Nítida" className="shrink-0">
      {tile && <rect width="64" height="64" rx="16" fill="var(--primary)" />}
      <motion.path
        d="M19.5 45 V19 L44.5 45 V31"
        fill="none"
        stroke={tile ? "var(--on-primary)" : "currentColor"}
        strokeWidth="7.5"
        strokeLinecap="round"
        strokeLinejoin="round"
        initial={draw ? { pathLength: 0 } : false}
        animate={{ pathLength: 1 }}
        transition={{ duration: 0.6, ease: [0.2, 0.8, 0.2, 1] }}
      />
      <motion.circle
        cx="44.5"
        cy="19"
        r="5"
        fill={tile ? "var(--brand-dot)" : "currentColor"}
        style={{ transformOrigin: "44.5px 19px" }}
        initial={draw ? { scale: 0 } : false}
        animate={{ scale: 1 }}
        transition={{ duration: 0.18, delay: draw ? 0.6 : 0, ease: [0.2, 0.8, 0.2, 1] }}
      />
    </svg>
  );
}

/** "nítida" en minúscula, la tilde de la í en menta. */
export function Wordmark({ className = "" }: { className?: string }) {
  return (
    <span className={`relative inline-flex items-baseline font-semibold tracking-[-0.02em] text-ink ${className}`} aria-label="nítida">
      <span aria-hidden>n</span>
      <span aria-hidden className="relative">
        ı
        <span className="absolute bottom-[1em] left-[0.08em] h-[0.21em] w-[0.11em] origin-bottom-left skew-x-[-28deg] rounded-[1px] bg-verified" />
      </span>
      <span aria-hidden>tida</span>
    </span>
  );
}

export function Lockup({ size = 32, animate = false, className = "" }: { size?: number; animate?: boolean; className?: string }) {
  return (
    <span className={`inline-flex items-center gap-2.5 ${className}`}>
      <Mark size={size} animate={animate} />
      <Wordmark className={size >= 36 ? "text-title" : "text-heading"} />
    </span>
  );
}
