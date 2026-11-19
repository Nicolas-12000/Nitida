"use client";
// Mapeo único de estados del dominio a estilo, ícono y texto (DESIGN.MD §2.4). Las pantallas no eligen colores.
import {
  BadgeCheck, CircleCheck, CircleDashed, CircleSlash, Fingerprint, OctagonAlert, PackageCheck, Pencil, Scale, ScanBarcode,
  ShieldCheck, TriangleAlert, Wrench, type LucideIcon,
} from "lucide-react";
import { animate, useMotionValue, useReducedMotion, useTransform, motion } from "motion/react";
import { useEffect } from "react";
import { Badge, type Tone } from "@/components/ui/primitives";
import { cop, plate as fmtPlate } from "@/lib/format";
import type { AlertType, CashCountStatus, EvidenceLevel, OrderStatus } from "@/lib/types";

export const ORDER_STATUS: Record<OrderStatus, { tone: Tone; icon: LucideIcon; label: string }> = {
  OPEN: { tone: "neutral", icon: CircleDashed, label: "Abierta" },
  IN_PROGRESS: { tone: "primary", icon: Wrench, label: "En proceso" },
  COMPLETED: { tone: "verified", icon: CircleCheck, label: "Lista para entregar" },
  DELIVERED: { tone: "neutral", icon: PackageCheck, label: "Entregada" },
  CANCELLED: { tone: "neutral", icon: CircleSlash, label: "Cancelada" },
};

export function OrderStatusBadge({ status }: { status: OrderStatus }) {
  const s = ORDER_STATUS[status];
  return <Badge tone={s.tone} icon={s.icon}>{s.label}</Badge>;
}

export const EVIDENCE: Record<EvidenceLevel, { tone: Tone; icon: LucideIcon; label: string; help: string }> = {
  MANUAL: { tone: "neutral", icon: Pencil, label: "Manual", help: "Escrito a mano, sin código ni foto." },
  SCANNED: { tone: "primary", icon: ScanBarcode, label: "Escaneado", help: "Código de barras leído. Es evidencia, no prueba de originalidad." },
  UNIQUE_SERIAL: { tone: "primary", icon: Fingerprint, label: "Serial único", help: "Serial que no puede estar activo en otra moto." },
  AUTHENTICITY_VERIFIED: { tone: "verified", icon: BadgeCheck, label: "Sello revisado", help: "Se revisó y fotografió el sello o empaque." },
};

export function EvidenceBadge({ level }: { level: EvidenceLevel }) {
  const e = EVIDENCE[level];
  return <Badge tone={e.tone} icon={e.icon}>{e.label}</Badge>;
}

export function CashStatusBadge({ status, difference }: { status: CashCountStatus; difference: number | null }) {
  if (status === "OPEN") return <Badge tone="neutral" icon={CircleDashed}>Sin cerrar</Badge>;
  if (status === "BALANCED") return <Badge tone="verified" icon={Scale}>Cuadrada</Badge>;
  return <Badge tone="attention" icon={Scale}>Descuadre de {cop(Math.abs(difference ?? 0))}</Badge>;
}

export const ALERT_COPY: Record<AlertType, { title: string; tone: Tone; icon: LucideIcon }> = {
  F1_INTAKE_WITHOUT_ORDER: { title: "Moto recibida sin orden", tone: "attention", icon: TriangleAlert },
  F2_CANCELLED_WITH_PARTS: { title: "Orden cancelada con repuestos", tone: "attention", icon: TriangleAlert },
  F3_CASH_MISMATCH: { title: "Descuadre en caja", tone: "attention", icon: TriangleAlert },
  F4_ZERO_TOTAL_WITHOUT_WARRANTY: { title: "Entrega sin cobro", tone: "attention", icon: TriangleAlert },
  F5_STALLED_ORDER: { title: "Orden quieta", tone: "attention", icon: TriangleAlert },
  F6_DUPLICATE_SERIAL: { title: "Serial repetido", tone: "danger", icon: OctagonAlert },
};

export function VerifiedChip() {
  return <Badge tone="verified" icon={ShieldCheck}>Verificado</Badge>;
}

/** Placa con aspecto de placa colombiana de moto, siempre en mayúsculas y con separador. */
export function PlateTag({ plate, size = "md" }: { plate: string | null | undefined; size?: "sm" | "md" | "lg" }) {
  const cls = {
    sm: "h-6 px-1.5 text-caption",
    md: "h-7 px-2 text-body-sm",
    lg: "h-10 px-3 text-heading",
  }[size];
  return (
    <span className={`inline-flex shrink-0 items-center rounded-sm border border-border-strong bg-surface-sunken font-mono font-medium tracking-wider text-ink ${cls}`}>
      {fmtPlate(plate)}
    </span>
  );
}

/** Conteo animado de montos (300 ms, tabular). */
export function AnimatedMoney({ value, className = "" }: { value: number; className?: string }) {
  const reduce = useReducedMotion();
  const mv = useMotionValue(value);
  const text = useTransform(mv, (v) => cop(Math.round(v)));
  useEffect(() => {
    if (reduce) {
      mv.set(value);
      return;
    }
    const c = animate(mv, value, { duration: 0.3, ease: [0.2, 0.8, 0.2, 1] });
    return () => c.stop();
  }, [value, reduce, mv]);
  return <motion.span className={`tabular ${className}`}>{text}</motion.span>;
}
