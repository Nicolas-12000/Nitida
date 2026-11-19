"use client";
import { Check, CircleAlert, Copy, Info, OctagonAlert, TriangleAlert, CircleCheck, RotateCw, type LucideIcon } from "lucide-react";
import { motion } from "motion/react";
import { useEffect, useRef, useState, type ReactNode } from "react";
import { messageFor, ApiError } from "@/lib/errors";
import { Button } from "./button";

export type Tone = "neutral" | "primary" | "verified" | "attention" | "danger";

const toneSoft: Record<Tone, string> = {
  neutral: "bg-surface-sunken text-ink-muted",
  primary: "bg-primary-soft text-primary-soft-ink",
  verified: "bg-verified-soft text-verified-ink",
  attention: "bg-attention-soft text-attention-ink",
  danger: "bg-danger-soft text-danger-ink",
};

export function Card({ children, className = "", as: As = "section" }: { children: ReactNode; className?: string; as?: "section" | "div" | "article" }) {
  return <As className={`card ${className}`}>{children}</As>;
}

export function CardHeader({ title, action, subtitle }: { title: ReactNode; subtitle?: ReactNode; action?: ReactNode }) {
  return (
    <div className="flex items-start justify-between gap-3 px-4 pt-4 pb-3 lg:px-5">
      <div className="min-w-0">
        <h2 className="text-heading font-semibold text-ink">{title}</h2>
        {subtitle && <p className="mt-0.5 text-body-sm text-ink-muted">{subtitle}</p>}
      </div>
      {action}
    </div>
  );
}

/** Chip de estado: soft + ink del rol, ícono de 14 px y texto. Nunca solo color. */
export function Badge({ tone = "neutral", icon: Icon, children, className = "" }: { tone?: Tone; icon?: LucideIcon; children: ReactNode; className?: string }) {
  return (
    <motion.span
      key={String(children)}
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      transition={{ duration: 0.12 }}
      className={`inline-flex h-6 shrink-0 items-center gap-1 rounded-xs px-2 text-caption font-medium whitespace-nowrap ${toneSoft[tone]} ${className}`}
    >
      {Icon && <Icon aria-hidden className="size-3.5" strokeWidth={1.75} />}
      {children}
    </motion.span>
  );
}

const bannerIcon: Record<Tone, LucideIcon> = {
  neutral: Info, primary: Info, verified: CircleCheck, attention: TriangleAlert, danger: OctagonAlert,
};
const bannerBorder: Record<Tone, string> = {
  neutral: "border-border-strong",
  primary: "border-primary/40",
  verified: "border-verified/40",
  attention: "border-attention/60",
  danger: "border-danger/40",
};

export function Banner({ tone = "neutral", title, children, action, role }: { tone?: Tone; title?: ReactNode; children?: ReactNode; action?: ReactNode; role?: "alert" | "status" }) {
  const Icon = bannerIcon[tone];
  return (
    <div role={role} className={`flex items-start gap-3 rounded-md border px-3 py-3 ${toneSoft[tone]} ${bannerBorder[tone]}`}>
      <Icon aria-hidden className="mt-0.5 size-5 shrink-0" strokeWidth={1.75} />
      <div className="min-w-0 flex-1 text-body-sm">
        {title && <p className="font-medium">{title}</p>}
        {children && <div className={title ? "mt-0.5 opacity-90" : ""}>{children}</div>}
      </div>
      {action && <div className="shrink-0">{action}</div>}
    </div>
  );
}

/** Error traducido (§9). En 5xx añade el código de soporte copiable. */
export function ErrorBanner({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  const support = error instanceof ApiError ? error.supportCode : undefined;
  return (
    <Banner
      tone="danger"
      role="alert"
      action={onRetry && <Button variant="ghost" size="sm" icon={RotateCw} onClick={onRetry}>Reintentar</Button>}
    >
      <p>{messageFor(error)}</p>
      {support && (
        <p className="mt-1 flex items-center gap-1">
          Código de soporte: <code className="font-mono">{support}</code> <CopyButton value={support} label="Copiar código de soporte" />
        </p>
      )}
    </Banner>
  );
}

export function Skeleton({ className = "" }: { className?: string }) {
  return <div aria-hidden className={`skeleton ${className}`} />;
}

export function SkeletonRows({ rows = 4, label }: { rows?: number; label: string }) {
  return (
    <div aria-busy="true" className="divide-y divide-border">
      <span className="sr-only">{label}</span>
      {Array.from({ length: rows }, (_, i) => (
        <div key={i} className="flex min-h-14 items-center justify-between gap-4 px-4 py-3 lg:px-5">
          <div className="flex flex-1 flex-col gap-2">
            <Skeleton className="h-3.5" />
            <Skeleton className="h-3 w-3/5" />
          </div>
          <Skeleton className="h-6 w-18" />
        </div>
      ))}
    </div>
  );
}

export function CopyButton({ value, label = "Copiar" }: { value: string; label?: string }) {
  const [done, setDone] = useState(false);
  return (
    <button
      type="button"
      aria-label={label}
      title={label}
      onClick={async () => {
        try {
          await navigator.clipboard.writeText(value);
          setDone(true);
          setTimeout(() => setDone(false), 1500);
        } catch {
          /* el portapapeles puede no estar disponible */
        }
      }}
      className="inline-flex size-8 items-center justify-center rounded-sm text-ink-muted transition-colors hover:bg-surface-sunken hover:text-ink"
    >
      {done ? <Check aria-hidden className="size-4 text-verified" strokeWidth={1.75} /> : <Copy aria-hidden className="size-4" strokeWidth={1.75} />}
    </button>
  );
}

export function PageHeader({ title, subtitle, action, back }: { title: ReactNode; subtitle?: ReactNode; action?: ReactNode; back?: ReactNode }) {
  return (
    <header className="mb-6 flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
      <div className="min-w-0">
        {back}
        <h1 className="text-title font-semibold text-ink">{title}</h1>
        {subtitle && <p className="mt-1 text-body-sm text-ink-muted">{subtitle}</p>}
      </div>
      {action && <div className="flex shrink-0 gap-2">{action}</div>}
    </header>
  );
}

export function EmptyState({ illustration, icon: Icon, title, line, action }: { illustration?: ReactNode; icon?: LucideIcon; title: string; line: string; action?: ReactNode }) {
  return (
    <motion.div
      initial={{ opacity: 0, y: 4 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.18, ease: [0.2, 0.8, 0.2, 1] }}
      className="flex flex-col items-center px-6 py-10 text-center"
    >
      {illustration ?? (
        Icon && (
          <span className="flex size-12 items-center justify-center rounded-full bg-surface-sunken">
            <Icon aria-hidden className="size-6 text-ink-muted" strokeWidth={1.75} />
          </span>
        )
      )}
      <h3 className="mt-4 text-heading font-medium text-ink">{title}</h3>
      <p className="mt-1 max-w-sm text-body-sm text-ink-muted">{line}</p>
      {action && <div className="mt-5">{action}</div>}
    </motion.div>
  );
}

/** Diálogo con <dialog> nativo: atrapa el foco, cierra con Esc y devuelve el foco al cerrar. */
export function Dialog({
  open, onClose, title, children, footer, destructive,
}: { open: boolean; onClose: () => void; title: string; children: ReactNode; footer?: ReactNode; destructive?: boolean }) {
  const ref = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    const d = ref.current;
    if (!d) return;
    if (open && !d.open) {
      d.showModal();
      if (destructive) d.querySelector<HTMLButtonElement>("[data-autofocus]")?.focus();
    }
    if (!open && d.open) d.close();
  }, [open, destructive]);
  return (
    <dialog
      ref={ref}
      onClose={onClose}
      onClick={(e) => e.target === ref.current && onClose()}
      className="m-auto mb-0 w-full max-w-none rounded-t-xl bg-surface p-0 text-ink shadow-overlay backdrop:backdrop-blur-none sm:mb-auto sm:max-w-[480px] sm:rounded-lg"
    >
      {open && (
        <motion.div initial={{ opacity: 0, y: 16 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.26, ease: [0.2, 0.8, 0.2, 1] }}>
          <div className="mx-auto mt-2 h-1 w-9 rounded-full bg-border-strong sm:hidden" aria-hidden />
          <div className="px-5 pt-4 pb-2 sm:pt-5">
            <h2 className="text-heading font-semibold">{title}</h2>
          </div>
          <div className="px-5 pb-4 text-body-sm text-ink-muted">{children}</div>
          {footer && <div className="flex flex-col-reverse gap-2 border-t border-border px-5 py-4 pb-safe sm:flex-row sm:justify-between">{footer}</div>}
        </motion.div>
      )}
    </dialog>
  );
}

export function Stat({ label, value, tone = "neutral", icon: Icon, hint }: { label: string; value: ReactNode; tone?: Tone; icon?: LucideIcon; hint?: ReactNode }) {
  const bg = { neutral: "bg-surface-sunken", primary: "bg-primary-soft", verified: "bg-verified-soft", attention: "bg-attention-soft", danger: "bg-danger-soft" }[tone];
  const ink = { neutral: "text-ink", primary: "text-primary-soft-ink", verified: "text-verified-ink", attention: "text-attention-ink", danger: "text-danger-ink" }[tone];
  return (
    <div className={`rounded-lg p-4 lg:p-5 ${bg}`}>
      <p className={`flex items-center gap-1.5 text-caption ${tone === "neutral" ? "text-ink-muted" : ink}`}>
        {Icon && <Icon aria-hidden className="size-4" strokeWidth={1.75} />}
        {label}
      </p>
      <p className={`mt-1 text-title font-semibold tabular lg:text-display ${ink}`}>{value}</p>
      {hint && <p className={`mt-0.5 text-caption ${tone === "neutral" ? "text-ink-muted" : ink} opacity-90`}>{hint}</p>}
    </div>
  );
}

export function FieldError({ children }: { children: ReactNode }) {
  return (
    <p className="flex items-start gap-1.5 text-caption text-danger-ink">
      <CircleAlert aria-hidden className="mt-0.5 size-4 shrink-0" strokeWidth={1.75} />
      {children}
    </p>
  );
}
