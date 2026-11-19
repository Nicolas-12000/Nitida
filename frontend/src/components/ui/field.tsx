"use client";
import { CircleAlert, TriangleAlert } from "lucide-react";
import { useId, type InputHTMLAttributes, type ReactNode, type SelectHTMLAttributes, type TextareaHTMLAttributes } from "react";

const control =
  "w-full rounded-md border bg-surface px-3 text-body text-ink placeholder:text-ink-subtle transition-[border-color,box-shadow] duration-[120ms] " +
  "focus:border-primary focus:outline-none focus:shadow-[0_0_0_3px_var(--ring)] disabled:opacity-60 lg:text-body-sm";

interface FieldProps {
  label: string;
  optional?: boolean;
  hint?: ReactNode;
  error?: string | null;
  /** Aviso mientras se escribe (no bloquea): por ejemplo, datos personales en un texto público. */
  warning?: string | null;
  prefix?: string;
  suffix?: string;
}

function Wrapper({ id, label, optional, hint, error, warning, children }: FieldProps & { id: string; children: ReactNode }) {
  return (
    <div className="flex flex-col gap-2">
      <label htmlFor={id} className="text-body-sm font-medium text-ink">
        {label}
        {optional && <span className="font-normal text-ink-muted"> (opcional)</span>}
      </label>
      {children}
      {error ? (
        <p id={`${id}-err`} className="flex items-start gap-1.5 text-caption text-danger-ink">
          <CircleAlert aria-hidden className="mt-0.5 size-4 shrink-0" strokeWidth={1.75} />
          {error}
        </p>
      ) : warning ? (
        <p id={`${id}-warn`} role="status" className="flex items-start gap-1.5 text-caption text-attention-ink">
          <TriangleAlert aria-hidden className="mt-0.5 size-4 shrink-0" strokeWidth={1.75} />
          {warning}
        </p>
      ) : hint ? (
        <p id={`${id}-hint`} className="text-caption text-ink-muted">{hint}</p>
      ) : null}
    </div>
  );
}

export function Input({ label, optional, hint, error, warning, prefix, suffix, className = "", ...rest }: FieldProps & InputHTMLAttributes<HTMLInputElement>) {
  const id = useId();
  return (
    <Wrapper id={id} label={label} optional={optional} hint={hint} error={error} warning={warning}>
      <div className="relative flex items-center">
        {prefix && <span className="pointer-events-none absolute left-3 text-body text-ink-muted lg:text-body-sm">{prefix}</span>}
        <input
          id={id}
          aria-invalid={error ? true : undefined}
          aria-describedby={error ? `${id}-err` : warning ? `${id}-warn` : hint ? `${id}-hint` : undefined}
          className={`${control} h-12 lg:h-10 ${error ? "border-danger" : warning ? "border-attention" : "border-border-input"} ${prefix ? "pl-7" : ""} ${suffix ? "pr-12" : ""} ${className}`}
          {...rest}
        />
        {suffix && <span className="pointer-events-none absolute right-3 text-body-sm text-ink-muted">{suffix}</span>}
      </div>
    </Wrapper>
  );
}

export function Textarea({ label, optional, hint, error, warning, className = "", ...rest }: FieldProps & TextareaHTMLAttributes<HTMLTextAreaElement>) {
  const id = useId();
  return (
    <Wrapper id={id} label={label} optional={optional} hint={hint} error={error} warning={warning}>
      <textarea
        id={id}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? `${id}-err` : warning ? `${id}-warn` : hint ? `${id}-hint` : undefined}
        className={`${control} min-h-24 py-2.5 ${error ? "border-danger" : warning ? "border-attention" : "border-border-input"} ${className}`}
        {...rest}
      />
    </Wrapper>
  );
}

export function Select({ label, optional, hint, error, children, className = "", ...rest }: FieldProps & SelectHTMLAttributes<HTMLSelectElement>) {
  const id = useId();
  return (
    <Wrapper id={id} label={label} optional={optional} hint={hint} error={error}>
      <select
        id={id}
        aria-invalid={error ? true : undefined}
        className={`${control} h-12 appearance-none bg-[length:16px] bg-[right_12px_center] bg-no-repeat pr-10 lg:h-10 ${error ? "border-danger" : "border-border-input"} ${className}`}
        style={{
          backgroundImage:
            "url(\"data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='%23878C99' stroke-width='1.75' stroke-linecap='round'%3E%3Cpath d='m6 9 6 6 6-6'/%3E%3C/svg%3E\")",
        }}
        {...rest}
      >
        {children}
      </select>
    </Wrapper>
  );
}

/** Grupo de opciones grandes y táctiles (radio) para elecciones cortas: medio de pago, rol, motivo. */
export function Choice<T extends string>({
  label, value, onChange, options, columns = 2,
}: { label: string; value: T | null; onChange: (v: T) => void; options: { value: T; label: string; hint?: string }[]; columns?: 2 | 3 | 4 }) {
  const name = useId();
  const cols = { 2: "grid-cols-2", 3: "grid-cols-3", 4: "grid-cols-2 sm:grid-cols-4" }[columns];
  return (
    <fieldset className="flex flex-col gap-2">
      <legend className="mb-2 text-body-sm font-medium text-ink">{label}</legend>
      <div className={`grid gap-2 ${cols}`}>
        {options.map((o) => (
          <label
            key={o.value}
            className={`flex min-h-12 cursor-pointer flex-col justify-center rounded-md border px-3 py-2 transition-colors duration-[120ms] has-[:focus-visible]:shadow-[0_0_0_3px_var(--ring)] ${
              value === o.value ? "border-primary bg-primary-soft text-primary-soft-ink" : "border-border-strong bg-surface text-ink hover:bg-surface-sunken"
            }`}
          >
            <input type="radio" name={name} value={o.value} checked={value === o.value} onChange={() => onChange(o.value)} className="sr-only" />
            <span className="text-body-sm font-medium">{o.label}</span>
            {o.hint && <span className="text-caption opacity-80">{o.hint}</span>}
          </label>
        ))}
      </div>
    </fieldset>
  );
}
