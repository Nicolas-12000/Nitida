"use client";
import { CheckCheck, Download, KeyRound, QrCode, ShieldCheck, Smartphone } from "lucide-react";
import { motion } from "motion/react";
import QRCode from "qrcode";
import { useEffect, useState } from "react";
import { toast } from "sonner";
import { api } from "@/lib/api";
import { messageFor } from "@/lib/errors";
import { useSession } from "@/lib/session";
import { currentStep, totpAt } from "@/lib/totp";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/field";
import { Badge, Banner, CopyButton } from "@/components/ui/primitives";
import type { TotpSetup } from "@/lib/types";

/**
 * Activar TOTP en tres pasos: QR (el secreto se muestra una vez), primer código y códigos de recuperación
 * (también una vez). Para el administrador de la plataforma es obligatorio antes de usar /admin.
 */
export function TotpSetupPanel() {
  const { me, reload, demo } = useSession();
  const [setup, setSetup] = useState<TotpSetup | null>(null);
  const [qr, setQr] = useState<string | null>(null);
  const [code, setCode] = useState("");
  const [recovery, setRecovery] = useState<string[] | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!setup) return;
    let alive = true;
    void QRCode.toDataURL(setup.otpauthUri, { margin: 1, width: 368, errorCorrectionLevel: "M", color: { dark: "#191F2E", light: "#FFFFFF" } }).then(
      (url) => alive && setQr(url),
    );
    return () => {
      alive = false;
    };
  }, [setup]);

  if (me.totpEnabled && !recovery) {
    return (
      <div className="flex items-start gap-3">
        <span className="flex size-10 shrink-0 items-center justify-center rounded-full bg-verified-soft text-verified-ink"><ShieldCheck aria-hidden className="size-5" strokeWidth={1.75} /></span>
        <div>
          <p className="flex items-center gap-2 text-body-sm font-medium text-ink">Verificación en dos pasos <Badge tone="verified">Activa</Badge></p>
          <p className="mt-0.5 text-body-sm text-ink-muted">Al ingresar le pediremos el código de su aplicación. Si pierde el celular, use uno de sus códigos de recuperación en el mismo campo.</p>
        </div>
      </div>
    );
  }

  if (recovery) {
    const text = `Códigos de recuperación de Nítida (${me.email})\nCada uno sirve una sola vez.\n\n${recovery.join("\n")}\n`;
    return (
      <motion.div initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} className="flex flex-col gap-4">
        <Banner tone="verified" title="Verificación en dos pasos activa">Guarde estos códigos en un lugar seguro. Cada uno reemplaza al código de la aplicación una sola vez.</Banner>
        <ol className="grid grid-cols-2 gap-2 rounded-md border border-border bg-surface-sunken p-4 font-mono text-body-sm tabular sm:grid-cols-4">
          {recovery.map((c) => <li key={c} className="text-ink">{c}</li>)}
        </ol>
        <Banner tone="attention">No los volveremos a mostrar.</Banner>
        <div className="flex flex-wrap gap-2">
          <Button icon={Download} onClick={() => {
            const url = URL.createObjectURL(new Blob([text], { type: "text/plain" }));
            const a = document.createElement("a");
            a.href = url;
            a.download = "nitida-codigos-de-recuperacion.txt";
            a.click();
            URL.revokeObjectURL(url);
          }}>Descargar códigos</Button>
          <span className="inline-flex items-center gap-1 text-body-sm text-ink-muted"><CopyButton value={text} label="Copiar códigos" /> Copiar</span>
          <Button variant="primary" icon={CheckCheck} className="ml-auto" onClick={async () => { setRecovery(null); await reload(); }}>Ya los guardé</Button>
        </div>
      </motion.div>
    );
  }

  if (!setup) {
    return (
      <div className="flex flex-col gap-4">
        <div className="flex items-start gap-3">
          <span className="flex size-10 shrink-0 items-center justify-center rounded-full bg-surface-sunken text-ink-muted"><Smartphone aria-hidden className="size-5" strokeWidth={1.75} /></span>
          <p className="text-body-sm text-ink-muted">
            Además de la contraseña, Nítida le pedirá un código de 6 dígitos de una aplicación como Google Authenticator, Microsoft Authenticator o Aegis.
            {me.platformAdmin && " Para administrar la plataforma es obligatoria."}
          </p>
        </div>
        {error && <Banner tone="danger">{error}</Banner>}
        <Button variant="primary" icon={QrCode} className="self-start" loading={busy} loadingText="Generando…" onClick={async () => {
          setBusy(true);
          setError(null);
          try {
            setSetup(await api.post<TotpSetup>("/auth/totp/setup"));
          } catch (e) {
            setError(messageFor(e));
          } finally {
            setBusy(false);
          }
        }}>Activar verificación en dos pasos</Button>
      </div>
    );
  }

  return (
    <div className="grid grid-cols-1 gap-6 md:grid-cols-[200px_minmax(0,1fr)]">
      <div className="flex flex-col items-center gap-2">
        <div className="flex size-[200px] items-center justify-center overflow-hidden rounded-md border border-border bg-surface">
          {/* El QR siempre va sobre blanco para que las cámaras lo lean también en modo oscuro. */}
          {/* eslint-disable-next-line @next/next/no-img-element -- data URL generado en el cliente, next/image no aporta */}
          {qr ? <img src={qr} alt="Código QR para su aplicación de autenticación" className="size-full" /> : <span className="skeleton size-[180px]" aria-hidden />}
        </div>
        <p className="text-center text-caption text-ink-muted">1. Escanéelo con su aplicación</p>
      </div>
      <form className="flex flex-col gap-4" onSubmit={async (e) => {
        e.preventDefault();
        if (!/^\d{6}$/.test(code)) return setError("Escriba los 6 dígitos que muestra la aplicación.");
        setBusy(true);
        setError(null);
        try {
          const r = await api.post<{ recoveryCodes: string[] }>("/auth/totp/confirm", { code });
          setRecovery(r.recoveryCodes);
          setSetup(null);
          toast.success("Verificación en dos pasos activada");
        } catch (err) {
          setError(messageFor(err));
        } finally {
          setBusy(false);
        }
      }}>
        <div>
          <p className="text-caption text-ink-muted">¿No puede escanear? Escriba esta clave en la aplicación:</p>
          <p className="mt-1 flex items-center gap-1 font-mono text-body-sm break-all text-ink">
            {setup.secret.replace(/(.{4})/g, "$1 ").trim()}<CopyButton value={setup.secret} label="Copiar clave" />
          </p>
        </div>
        <Input label="2. Código de la aplicación" inputMode="numeric" autoComplete="one-time-code" placeholder="123456" maxLength={6}
          value={code} onChange={(e) => setCode(e.target.value.replace(/\D/g, "").slice(0, 6))} className="font-mono tracking-[0.3em] tabular" />
        {demo && <DemoCode secret={setup.secret} onUse={setCode} />}
        {error && <Banner tone="danger" role="alert">{error}</Banner>}
        <Button type="submit" variant="primary" icon={KeyRound} loading={busy} loadingText="Comprobando…" className="self-start">Activar</Button>
      </form>
    </div>
  );
}

/** Solo en modo demostración: el código vigente, para probar sin instalar una aplicación. */
function DemoCode({ secret, onUse }: { secret: string; onUse: (c: string) => void }) {
  const [code, setCode] = useState<string | null>(null);
  useEffect(() => {
    let alive = true;
    const tick = () => void totpAt(secret, currentStep()).then((c) => alive && setCode(c));
    tick();
    const t = setInterval(tick, 5000);
    return () => {
      alive = false;
      clearInterval(t);
    };
  }, [secret]);
  return (
    <p className="text-caption text-ink-muted">
      Demostración: el código vigente es <button type="button" className="font-mono font-medium text-primary hover:underline" onClick={() => code && onUse(code)}>{code ?? "……"}</button>
    </p>
  );
}
