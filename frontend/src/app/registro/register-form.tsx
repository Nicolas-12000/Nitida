"use client";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { Building2, UserPlus } from "lucide-react";
import { useState } from "react";
import { api, isDemo } from "@/lib/api";
import { isCode, messageFor } from "@/lib/errors";
import { Button } from "@/components/ui/button";
import { Choice, Input } from "@/components/ui/field";
import { Banner } from "@/components/ui/primitives";
import type { Me } from "@/lib/types";

/** Registro en dos pasos: la cuenta y, si es un taller, sus datos (queda como dueño, pendiente de verificación). */
export function RegisterForm() {
  const router = useRouter();
  const [step, setStep] = useState<1 | 2>(1);
  const [kind, setKind] = useState<"WORKSHOP" | "RIDER">("WORKSHOP");
  const [f, setF] = useState({ displayName: "", email: "", password: "" });
  const [w, setW] = useState({ name: "", documentNumber: "", address: "", municipality: "", phone: "" });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const account = async () => {
    const e: Record<string, string> = {};
    if (!f.displayName.trim()) e.displayName = "Escriba su nombre.";
    if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(f.email)) e.email = "Revise el correo: no parece válido.";
    if (f.password.length < 12) e.password = "Mínimo 12 caracteres. Una frase corta es más fácil de recordar.";
    setErrors(e);
    if (Object.keys(e).length) return;
    setBusy(true);
    setError(null);
    try {
      await api.post("/auth/register", f);
      const me = await api.post<Me>("/auth/login", { email: f.email, password: f.password });
      if (kind === "RIDER") router.replace("/mis-motos");
      else {
        void me;
        setStep(2);
      }
    } catch (err) {
      setError(isCode(err, "EMAIL_TAKEN") ? "Ya existe una cuenta con ese correo. Ingrese o recupere su contraseña." : messageFor(err));
    } finally {
      setBusy(false);
    }
  };

  const workshop = async () => {
    const e: Record<string, string> = {};
    for (const [k, label] of [["name", "el nombre"], ["documentNumber", "el NIT o la cédula"], ["address", "la dirección"], ["municipality", "el municipio"], ["phone", "el celular"]] as const) {
      if (!w[k].trim()) e[k] = `Escriba ${label}.`;
    }
    setErrors(e);
    if (Object.keys(e).length) return;
    setBusy(true);
    setError(null);
    try {
      await api.post("/workshops", w);
      router.replace("/inicio");
    } catch (err) {
      setError(messageFor(err));
    } finally {
      setBusy(false);
    }
  };

  if (step === 2) {
    return (
      <form className="flex flex-col gap-4" onSubmit={(e) => { e.preventDefault(); void workshop(); }}>
        <Banner tone="neutral">Cuenta creada. Ahora los datos del taller: quedará como dueño y Nítida verificará los documentos.</Banner>
        <Input label="Nombre del taller" placeholder="Motos El Ejido" value={w.name} onChange={(e) => setW({ ...w, name: e.target.value })} error={errors.name} />
        <Input label="NIT o cédula" placeholder="900481227-3" value={w.documentNumber} onChange={(e) => setW({ ...w, documentNumber: e.target.value })} error={errors.documentNumber} />
        <Input label="Dirección" placeholder="Cra. 27 #18-42" value={w.address} onChange={(e) => setW({ ...w, address: e.target.value })} error={errors.address} />
        <div className="grid grid-cols-2 gap-3">
          <Input label="Municipio" placeholder="Pasto" value={w.municipality} onChange={(e) => setW({ ...w, municipality: e.target.value })} error={errors.municipality} />
          <Input label="Celular" inputMode="numeric" placeholder="3001234567" value={w.phone} onChange={(e) => setW({ ...w, phone: e.target.value.replace(/\D/g, "").slice(0, 10) })} error={errors.phone} />
        </div>
        {error && <Banner tone="danger" role="alert">{error}</Banner>}
        <Button type="submit" variant="primary" size="lg" icon={Building2} loading={busy} loadingText="Registrando…">Registrar taller</Button>
      </form>
    );
  }

  return (
    <form className="flex flex-col gap-4" onSubmit={(e) => { e.preventDefault(); void account(); }} noValidate>
      <Choice label="¿Para qué la va a usar?" value={kind} onChange={setKind}
        options={[{ value: "WORKSHOP", label: "Tengo un taller", hint: "Recepción, órdenes y caja" }, { value: "RIDER", label: "Tengo una moto", hint: "Ver y compartir su historial" }]} />
      <Input label="Su nombre" autoComplete="name" placeholder="Carolina Benavides" value={f.displayName} onChange={(e) => setF({ ...f, displayName: e.target.value })} error={errors.displayName} />
      <Input label="Correo" type="email" inputMode="email" autoComplete="email" placeholder="nombre@taller.co" value={f.email} onChange={(e) => setF({ ...f, email: e.target.value })} error={errors.email} />
      <Input label="Contraseña" type="password" autoComplete="new-password" value={f.password} onChange={(e) => setF({ ...f, password: e.target.value })} error={errors.password}
        hint="Entre 12 y 128 caracteres. Rechazamos las contraseñas más comunes." />
      {error && <Banner tone="danger" role="alert">{error}</Banner>}
      {isDemo() && <Banner tone="neutral">Está en modo demostración: la cuenta solo existirá en este navegador.</Banner>}
      <Button type="submit" variant="primary" size="lg" icon={UserPlus} loading={busy} loadingText="Creando cuenta…">Crear cuenta</Button>
      <p className="text-center text-body-sm text-ink-muted">¿Ya tiene cuenta? <Link href="/login" className="font-medium text-primary hover:underline">Ingrese</Link></p>
    </form>
  );
}
