"use client";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { motion } from "motion/react";
import { ArrowLeftRight, Bike, ChevronRight, KeyRound } from "lucide-react";
import { useState } from "react";
import { toast } from "sonner";
import { api } from "@/lib/api";
import { isCode, messageFor } from "@/lib/errors";
import { date, motoName } from "@/lib/format";
import { invalidate, useQuery } from "@/lib/use-query";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/field";
import { Badge, Banner, Card, CardHeader, EmptyState, ErrorBanner, PageHeader, SkeletonRows } from "@/components/ui/primitives";
import { PlateTag } from "@/components/nitida/status";
import { ChainHero } from "@/components/illustrations";
import type { OwnedMotorcycle } from "@/lib/types";

export default function MisMotosPage() {
  const router = useRouter();
  const motos = useQuery<OwnedMotorcycle[]>("owner:motos", () => api.get("/owner/motorcycles"));
  const [code, setCode] = useState("");
  const [transfer, setTransfer] = useState("");
  const [busy, setBusy] = useState<"claim" | "transfer" | null>(null);
  const [error, setError] = useState<{ which: "claim" | "transfer"; text: string } | null>(null);

  const claim = async () => {
    const c = code.replace(/\s/g, "").toUpperCase();
    if (c.length !== 8) return setError({ which: "claim", text: "El código tiene 8 caracteres. Está impreso en el recibo de entrega." });
    setBusy("claim");
    setError(null);
    try {
      const r = await api.post<{ motorcycleId: string }>("/owner/claims", { code: c });
      invalidate("owner:");
      toast.success("Moto agregada a su cuenta");
      router.push(`/mis-motos/${r.motorcycleId}`);
    } catch (e) {
      setError({ which: "claim", text: isCode(e, "NOT_FOUND") ? "Ese código no está disponible: puede estar mal escrito, vencido o ya usado." : messageFor(e) });
    } finally {
      setBusy(null);
    }
  };

  const accept = async () => {
    setBusy("transfer");
    setError(null);
    try {
      const r = await api.post<{ motorcycleId: string }>(`/owner/transfers/${encodeURIComponent(transfer.trim().toUpperCase())}/accept`);
      invalidate("owner:");
      toast.success("Traspaso aceptado. La moto ya está en su cuenta.");
      router.push(`/mis-motos/${r.motorcycleId}`);
    } catch (e) {
      setError({ which: "transfer", text: isCode(e, "NOT_FOUND") ? "Ese código de traspaso no está disponible: puede estar vencido o ya usado." : messageFor(e) });
    } finally {
      setBusy(null);
    }
  };

  const list = motos.data ?? [];
  return (
    <>
      <PageHeader title="Mis motos" subtitle="El historial de sus motos, firmado por los talleres que las atendieron. Compártalo cuando vaya a venderla." />
      <div className="grid grid-cols-1 gap-4 lg:grid-cols-3 lg:gap-6">
        <Card className="lg:col-span-2">
          {motos.error ? <div className="p-4"><ErrorBanner error={motos.error} onRetry={motos.refetch} /></div> : motos.loading ? <SkeletonRows rows={2} label="Cargando sus motos…" /> : list.length === 0 ? (
            <EmptyState illustration={<ChainHero className="w-64" />} title="Agregue su moto" line="Use el código que le dio el taller al entregarla." />
          ) : (
            <ul className="divide-y divide-border">
              {list.map((m, i) => (
                <motion.li key={m.motorcycleId} initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: i * 0.06 }}>
                  <Link href={`/mis-motos/${m.motorcycleId}`} className="flex min-h-20 items-center gap-4 px-4 py-4 hover:bg-surface-sunken lg:px-5">
                    <span className="flex size-12 shrink-0 items-center justify-center rounded-lg bg-primary-soft text-primary-soft-ink"><Bike aria-hidden className="size-6" strokeWidth={1.75} /></span>
                    <div className="min-w-0 flex-1">
                      <p className="text-body font-medium text-ink">{motoName(m)}{m.modelYear ? ` · ${m.modelYear}` : ""}</p>
                      <p className="text-caption text-ink-muted">{m.active ? `Suya desde el ${date(m.since)}` : `Fue suya hasta el ${date(m.until)}`}</p>
                    </div>
                    <PlateTag plate={m.plate} />
                    {!m.active && <Badge tone="neutral">Vendida</Badge>}
                    <ChevronRight aria-hidden className="size-4 text-ink-muted" />
                  </Link>
                </motion.li>
              ))}
            </ul>
          )}
        </Card>
        <div className="flex flex-col gap-4">
          <Card>
            <CardHeader title="Agregar con código" subtitle="Viene impreso en el recibo de entrega del taller." />
            <form className="flex flex-col gap-3 px-4 pb-4 lg:px-5" onSubmit={(e) => { e.preventDefault(); void claim(); }}>
              <Input label="Código del recibo" placeholder="K7M2QX9P" autoCapitalize="characters" autoComplete="off" maxLength={8} value={code}
                onChange={(e) => setCode(e.target.value.toUpperCase())} className="font-mono tracking-[0.2em]" error={error?.which === "claim" ? error.text : null} />
              <Button type="submit" variant="primary" icon={KeyRound} loading={busy === "claim"} loadingText="Agregando…">Agregar moto</Button>
            </form>
          </Card>
          <Card>
            <CardHeader title="Recibir un traspaso" subtitle="Si compró una moto, pídale al vendedor su código de traspaso." />
            <form className="flex flex-col gap-3 px-4 pb-4 lg:px-5" onSubmit={(e) => { e.preventDefault(); void accept(); }}>
              <Input label="Código de traspaso" placeholder="T4QW8ZKM2R" autoCapitalize="characters" autoComplete="off" value={transfer}
                onChange={(e) => setTransfer(e.target.value.toUpperCase())} className="font-mono tracking-wider" error={error?.which === "transfer" ? error.text : null} />
              <Button type="submit" icon={ArrowLeftRight} loading={busy === "transfer"} loadingText="Aceptando…">Aceptar traspaso</Button>
            </form>
          </Card>
          <Banner tone="neutral">Su nombre nunca aparece en el historial público. Si vende la moto, deja de ver lo que se registre después de la venta.</Banner>
        </div>
      </div>
    </>
  );
}
