"use client";
import { Inbox } from "lucide-react";
import { useEffect, useState } from "react";
import { isDemo } from "@/lib/api";
import { CopyButton } from "@/components/ui/primitives";

interface Mail { to: string; subject: string; code: string; at: string }

/**
 * Solo en modo demostración: el backend real manda el código por correo (Mailpit en desarrollo) y nunca lo devuelve.
 * La demostración no tiene correo, así que muestra aquí el mensaje que habría llegado, para poder seguir el flujo.
 */
export function DemoMail({ to }: { to: string }) {
  const [mail, setMail] = useState<Mail | null>(null);
  useEffect(() => {
    if (!isDemo()) return;
    let alive = true;
    void import("@/lib/demo/server").then(({ demoLastMail }) => alive && setMail(demoLastMail(to)));
    return () => {
      alive = false;
    };
  }, [to]);
  if (!mail) return null;
  return (
    <div className="rounded-md border border-dashed border-border-strong bg-surface-sunken p-3">
      <p className="flex items-center gap-1.5 text-caption font-medium text-ink-muted">
        <Inbox aria-hidden className="size-4" strokeWidth={1.75} /> Buzón de demostración · para {mail.to}
      </p>
      <p className="mt-1 text-caption text-ink-muted">{mail.subject}</p>
      <p className="mt-1 flex items-center gap-1 font-mono text-body-sm break-all text-ink">
        {mail.code}<CopyButton value={mail.code} label="Copiar código del correo" />
      </p>
    </div>
  );
}
