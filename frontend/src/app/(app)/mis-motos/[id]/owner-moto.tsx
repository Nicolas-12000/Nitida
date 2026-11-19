"use client";
import Link from "next/link";
import { useParams } from "next/navigation";
import { ArrowLeft, ArrowLeftRight, Share2 } from "lucide-react";
import { useState } from "react";
import { api } from "@/lib/api";
import { isCode, messageFor } from "@/lib/errors";
import { date, motoName } from "@/lib/format";
import { useQuery } from "@/lib/use-query";
import { Button } from "@/components/ui/button";
import { Banner, Card, CardHeader, CopyButton, Dialog, EmptyState, ErrorBanner, PageHeader } from "@/components/ui/primitives";
import { PlateTag } from "@/components/nitida/status";
import { EvidenceSummary, HistoryTimeline, SealSkeleton, TimelineSkeleton, VerificationSeal, sealDetail } from "@/components/nitida/history";
import { ShareLinkDialog } from "@/components/nitida/share-link";
import { BrokenLinkFigure } from "@/components/illustrations";
import type { HistoryView, OwnedMotorcycle } from "@/lib/types";

export function OwnerMoto() {
  const { id } = useParams<{ id: string }>();
  const h = useQuery<HistoryView>(`owner:history:${id}`, () => api.get(`/owner/motorcycles/${id}/history`));
  const motos = useQuery<OwnedMotorcycle[]>("owner:motos", () => api.get("/owner/motorcycles"));
  const mine = motos.data?.find((m) => m.motorcycleId === id);
  const [share, setShare] = useState(false);
  const [transfer, setTransfer] = useState(false);

  if (h.error) {
    return isCode(h.error, "NOT_FOUND")
      ? <EmptyState illustration={<BrokenLinkFigure />} title="No encontramos esta moto" line="Solo se ven las motos que están o estuvieron en su cuenta." action={<Link href="/mis-motos" className="font-medium text-primary">Ir a Mis motos</Link>} />
      : <ErrorBanner error={h.error} onRetry={h.refetch} />;
  }
  const d = h.data;
  return (
    <>
      <PageHeader
        back={<Link href="/mis-motos" className="mb-2 inline-flex items-center gap-1 text-body-sm text-ink-muted hover:text-ink"><ArrowLeft aria-hidden className="size-4" /> Mis motos</Link>}
        title={<span className="flex flex-wrap items-center gap-3">{d ? motoName(d.motorcycle) : "Su moto"}{d && <PlateTag plate={d.motorcycle.plate} />}</span>}
        subtitle={mine ? (mine.active ? `Suya desde el ${date(mine.since)}` : `La vendió el ${date(mine.until)}. Solo ve lo registrado hasta entonces.`) : undefined}
        action={mine?.active && (
          <>
            <Button icon={ArrowLeftRight} onClick={() => setTransfer(true)}>Vender o traspasar</Button>
            <Button variant="primary" icon={Share2} onClick={() => setShare(true)}>Compartir historial</Button>
          </>
        )}
      />
      <div className="grid grid-cols-1 gap-4 lg:grid-cols-3 lg:gap-6">
        <Card className="order-2 lg:order-1 lg:col-span-2">
          <CardHeader title="Servicios" />
          <div className="px-4 pb-5 lg:px-5">{d ? <HistoryTimeline history={d} /> : <TimelineSkeleton />}</div>
        </Card>
        <Card className="order-1 p-6 lg:order-2">
          {d ? <VerificationSeal verification={d.verification} detail={sealDetail(d)} /> : <SealSkeleton />}
          {d && <div className="mt-6"><EvidenceSummary history={d} /></div>}
        </Card>
      </div>
      <ShareLinkDialog open={share} onClose={() => setShare(false)} endpoint={`/owner/motorcycles/${id}/links`} revocable={false} />
      <TransferDialog open={transfer} motoId={id} onClose={() => setTransfer(false)} />
    </>
  );
}

function TransferDialog({ open, motoId, onClose }: { open: boolean; motoId: string; onClose: () => void }) {
  const [code, setCode] = useState<{ code: string; expiresAt: string } | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const close = () => { setCode(null); setError(null); onClose(); };
  return (
    <Dialog open={open} onClose={close} destructive={!code} title={code ? "Código de traspaso" : "¿Traspasar esta moto?"}
      footer={code ? <><span /><Button variant="primary" onClick={close}>Listo</Button></> : <><Button variant="ghost" data-autofocus onClick={close}>Volver</Button>
        <Button variant="primary" icon={ArrowLeftRight} loading={busy} loadingText="Generando…" onClick={async () => {
          setBusy(true);
          try {
            setCode(await api.post(`/owner/motorcycles/${motoId}/transfers`));
          } catch (e) {
            setError(messageFor(e));
          } finally {
            setBusy(false);
          }
        }}>Generar código</Button></>}>
      {!code ? (
        <>
          <p>Cuando el comprador acepte el código, la moto pasa a su cuenta. Usted conserva lo registrado hasta ese día; lo de después ya no lo verá.</p>
          <p className="mt-2">El historial público solo dirá que la moto cambió de propietario, sin nombres.</p>
          {error && <div className="mt-3"><Banner tone="danger">{error}</Banner></div>}
        </>
      ) : (
        <div className="flex flex-col items-center gap-3 text-center text-ink">
          <p className="flex items-center gap-2 font-mono text-display font-semibold tracking-[0.15em]">{code.code}<CopyButton value={code.code} label="Copiar código de traspaso" /></p>
          <p className="text-caption text-ink-muted">Vence el {date(code.expiresAt)}. Entrégueselo solo al comprador.</p>
          <Banner tone="attention">Este código se muestra una sola vez.</Banner>
        </div>
      )}
    </Dialog>
  );
}
