"use client";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { motion } from "motion/react";
import { ArrowLeft, ArrowRight, Bike, Building2, ClipboardList, Inbox, KeyRound, LogIn, ShieldCheck, Wrench, type LucideIcon } from "lucide-react";
import { useEffect, useState } from "react";
import { api, demoAllowed, setDemo } from "@/lib/api";
import { isCode, messageFor } from "@/lib/errors";
import { clearCache } from "@/lib/use-query";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/field";
import { Banner } from "@/components/ui/primitives";
import { DEMO_ACCOUNTS } from "@/lib/demo/accounts";
import type { Me } from "@/lib/types";

const ICONS: Record<string, LucideIcon> = { "u-owner": ClipboardList, "u-desk": Inbox, "u-mech": Wrench, "u-rider": Bike, "u-admin": Building2 };

function destination(me: Me, next: string | null) {
  if (next && next.startsWith("/") && !next.startsWith("//")) return next;
  if (me.memberships.length) return "/inicio";
  if (me.platformAdmin) return "/admin";
  return "/mis-motos";
}

export function LoginForm() {
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<{ text: string; offline?: boolean } | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [next, setNext] = useState<string | null>(null);

  useEffect(() => {
    const p = new URLSearchParams(window.location.search);
    // Parámetros de la URL: solo existen en el navegador.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setNext(p.get("siguiente"));
    if (p.get("motivo") === "sesion") setNotice("Su sesión terminó. Ingrese de nuevo para continuar.");
    if (p.get("motivo") === "conexion") setNotice("No pudimos comunicarnos con el servidor de Nítida. Revise la conexión o pruebe el modo demostración.");
  }, []);

  /** Un 401 con TOTP_REQUIRED (solo con la contraseña correcta) pide el código y se reenvía el mismo formulario. */
  const [pending, setPending] = useState<null | { kind: "form" } | { kind: "demo"; userId: string }>(null);
  const [totp, setTotp] = useState("");

  const attempt = async (target: { kind: "form" } | { kind: "demo"; userId: string }, code?: string) => {
    setBusy(target.kind === "form" ? "form" : target.userId);
    setError(null);
    setDemo(target.kind === "demo");
    clearCache();
    try {
      const me = target.kind === "form"
        ? await api.post<Me>("/auth/login", { email: email.trim(), password, totp: code || undefined })
        : await api.post<Me>("/auth/demo-login", { userId: target.userId, totp: code || undefined });
      router.replace(destination(me, target.kind === "form" ? next : null));
    } catch (e) {
      if (isCode(e, "TOTP_REQUIRED")) {
        setPending(target);
        setTotp("");
      } else {
        setError({ text: messageFor(e), offline: isCode(e, "NETWORK_ERROR", "INTERNAL_ERROR", "UNKNOWN") });
      }
      setBusy(null);
    }
  };

  const submit = async () => {
    if (!email || !password) return setError({ text: "Escriba su correo y su contraseña." });
    await attempt({ kind: "form" });
  };

  const demo = (userId: string) => attempt({ kind: "demo", userId });

  if (pending) {
    return (
      <form className="flex flex-col gap-4" onSubmit={(e) => {
        e.preventDefault();
        const code = totp.replace(/\s/g, "");
        if (code.length < 6) return setError({ text: "Escriba los 6 dígitos de su aplicación o un código de recuperación." });
        void attempt(pending, code);
      }}>
        <div className="flex items-start gap-3 rounded-md bg-surface-sunken p-3">
          <ShieldCheck aria-hidden className="mt-0.5 size-5 shrink-0 text-primary" strokeWidth={1.75} />
          <p className="text-body-sm text-ink-muted">Su cuenta tiene verificación en dos pasos. Abra su aplicación de autenticación y escriba el código de 6 dígitos.</p>
        </div>
        <Input label="Código de verificación" inputMode="text" autoComplete="one-time-code" autoFocus placeholder="123456" value={totp}
          onChange={(e) => setTotp(e.target.value.toUpperCase().slice(0, 20))} className="font-mono tracking-[0.3em] tabular"
          hint="¿Perdió el celular? Escriba uno de sus códigos de recuperación en este mismo campo. Cada uno sirve una vez." />
        {error && <Banner tone="danger" role="alert">{error.text}</Banner>}
        <Button type="submit" variant="primary" size="lg" icon={KeyRound} loading={!!busy} loadingText="Comprobando…">Verificar e ingresar</Button>
        <Button variant="ghost" icon={ArrowLeft} onClick={() => { setPending(null); setError(null); }}>Volver</Button>
      </form>
    );
  }

  return (
    <div className="flex flex-col gap-8">
      <form className="flex flex-col gap-4" onSubmit={(e) => { e.preventDefault(); void submit(); }} noValidate>
        {notice && <Banner tone="neutral" role="status">{notice}</Banner>}
        <Input label="Correo" type="email" inputMode="email" autoComplete="username" placeholder="nombre@taller.co" value={email} onChange={(e) => setEmail(e.target.value)} />
        <Input label="Contraseña" type="password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} />
        <Link href="/recuperar" className="-mt-2 self-end text-body-sm font-medium text-primary hover:underline">¿Olvidó su contraseña?</Link>
        {error && (
          <Banner tone="danger" role="alert">
            {error.text}
            {error.offline && demoAllowed && " Mientras tanto puede recorrer Nítida en modo demostración."}
          </Banner>
        )}
        <Button type="submit" variant="primary" size="lg" icon={LogIn} loading={busy === "form"} loadingText="Ingresando…">Ingresar</Button>
        <p className="text-center text-body-sm text-ink-muted">¿Su taller aún no usa Nítida? <Link href="/registro" className="font-medium text-primary hover:underline">Cree una cuenta</Link></p>
      </form>

      {demoAllowed && (
        <section aria-labelledby="demo-title" className="border-t border-border pt-6">
          <h2 id="demo-title" className="text-body-sm font-semibold text-ink">Probar sin servidor</h2>
          <p className="mt-1 text-caption text-ink-muted">Entre con un rol al taller de ejemplo “Motos El Ejido” (Pasto). Los datos son ficticios y viven solo en este navegador.</p>
          <ul className="mt-3 flex flex-col gap-2">
            {DEMO_ACCOUNTS.map((a, i) => {
              const Icon = ICONS[a.id];
              return (
                <motion.li key={a.id} initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.4 + i * 0.05, duration: 0.26 }}>
                  <button
                    type="button"
                    onClick={() => demo(a.id)}
                    disabled={!!busy}
                    className="group flex min-h-14 w-full items-center gap-3 rounded-md border border-border bg-surface px-3 py-2 text-left transition-colors hover:border-primary hover:bg-primary-soft/40 disabled:opacity-60"
                  >
                    <span className="flex size-9 shrink-0 items-center justify-center rounded-sm bg-surface-sunken text-ink-muted group-hover:text-primary">
                      <Icon aria-hidden className="size-[18px]" strokeWidth={1.75} />
                    </span>
                    <span className="min-w-0 flex-1">
                      <span className="block text-body-sm font-medium text-ink">{a.role} <span className="font-normal text-ink-muted">· {a.name}</span></span>
                      <span className="block truncate text-caption text-ink-muted">{a.hint}</span>
                    </span>
                    {busy === a.id ? <span className="size-4 animate-spin rounded-full border-2 border-primary border-t-transparent" aria-label="Ingresando" /> : <ArrowRight aria-hidden className="size-4 text-ink-muted transition-transform group-hover:translate-x-0.5 group-hover:text-primary" />}
                  </button>
                </motion.li>
              );
            })}
          </ul>
        </section>
      )}
    </div>
  );
}
