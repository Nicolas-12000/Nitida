"use client";
import Link from "next/link";
import { ArrowLeft, KeyRound, LogIn, Mail } from "lucide-react";
import { AnimatePresence, motion } from "motion/react";
import { useState } from "react";
import { api } from "@/lib/api";
import { isCode, messageFor } from "@/lib/errors";
import { Button, ButtonLink } from "@/components/ui/button";
import { Input } from "@/components/ui/field";
import { Banner } from "@/components/ui/primitives";
import { DemoMail } from "@/components/nitida/demo-mail";

/**
 * Recuperar la contraseña: 1) pedir el código (la respuesta es igual exista o no la cuenta, para no revelar correos),
 * 2) escribir el código del correo y la contraseña nueva.
 */
export function RecoverForm() {
  const [step, setStep] = useState<"email" | "code" | "done">("email");
  const [email, setEmail] = useState("");
  const [token, setToken] = useState("");
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const request = async () => {
    if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email.trim())) return setError("Revise el correo: no parece válido.");
    setBusy(true);
    setError(null);
    try {
      await api.post("/auth/password/forgot", { email: email.trim() });
      setStep("code");
    } catch (e) {
      setError(messageFor(e));
    } finally {
      setBusy(false);
    }
  };

  const reset = async () => {
    if (!token.trim()) return setError("Pegue el código que llegó a su correo.");
    if (password.length < 12) return setError("La contraseña nueva debe tener al menos 12 caracteres.");
    if (password !== confirm) return setError("Las dos contraseñas no coinciden.");
    setBusy(true);
    setError(null);
    try {
      await api.post("/auth/password/reset", { token: token.trim(), password });
      setStep("done");
    } catch (e) {
      setError(isCode(e, "NOT_FOUND") ? "Ese código no sirve: puede estar mal copiado, haber vencido (1 hora) o ya se usó. Pida uno nuevo." : messageFor(e));
    } finally {
      setBusy(false);
    }
  };

  return (
    <AnimatePresence mode="wait" initial={false}>
      {step === "email" && (
        <motion.form key="email" initial={{ opacity: 0, x: -8 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, x: -8 }} transition={{ duration: 0.18 }}
          className="flex flex-col gap-4" onSubmit={(e) => { e.preventDefault(); void request(); }} noValidate>
          <Input label="Correo de su cuenta" type="email" inputMode="email" autoComplete="email" placeholder="nombre@taller.co" value={email} onChange={(e) => setEmail(e.target.value)} />
          {error && <Banner tone="danger" role="alert">{error}</Banner>}
          <Button type="submit" variant="primary" size="lg" icon={Mail} loading={busy} loadingText="Enviando…">Enviarme un código</Button>
          <Link href="/login" className="inline-flex items-center justify-center gap-1 text-body-sm text-ink-muted hover:text-ink"><ArrowLeft aria-hidden className="size-4" /> Volver a ingresar</Link>
        </motion.form>
      )}
      {step === "code" && (
        <motion.form key="code" initial={{ opacity: 0, x: 8 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, x: 8 }} transition={{ duration: 0.18 }}
          className="flex flex-col gap-4" onSubmit={(e) => { e.preventDefault(); void reset(); }} noValidate>
          <Banner tone="neutral" title="Revise su correo">
            Si <span className="font-medium">{email}</span> tiene cuenta en Nítida, le llegó un código que vence en una hora. Si no aparece, revise la carpeta de spam.
          </Banner>
          <DemoMail to={email} />
          <Input label="Código del correo" autoComplete="one-time-code" spellCheck={false} placeholder="Pegue aquí el código" value={token} onChange={(e) => setToken(e.target.value)} className="font-mono" />
          <Input label="Contraseña nueva" type="password" autoComplete="new-password" value={password} onChange={(e) => setPassword(e.target.value)} hint="Entre 12 y 128 caracteres. Rechazamos las contraseñas más comunes." />
          <Input label="Repita la contraseña nueva" type="password" autoComplete="new-password" value={confirm} onChange={(e) => setConfirm(e.target.value)} />
          {error && <Banner tone="danger" role="alert">{error}</Banner>}
          <Button type="submit" variant="primary" size="lg" icon={KeyRound} loading={busy} loadingText="Guardando…">Cambiar contraseña</Button>
          <Button variant="ghost" onClick={() => { setStep("email"); setError(null); }}>Pedir otro código</Button>
        </motion.form>
      )}
      {step === "done" && (
        <motion.div key="done" initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} className="flex flex-col gap-4">
          <Banner tone="verified" title="Contraseña cambiada">Ya puede ingresar con la contraseña nueva.</Banner>
          <ButtonLink href="/login" variant="primary" size="lg" icon={LogIn}>Ingresar</ButtonLink>
        </motion.div>
      )}
    </AnimatePresence>
  );
}
