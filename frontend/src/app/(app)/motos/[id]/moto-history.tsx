"use client";
import Link from "next/link";
import { useParams } from "next/navigation";
import { ArrowLeft, FilePen, Gauge, ImagePlus, Share2 } from "lucide-react";
import { useState } from "react";
import { toast } from "sonner";
import { api } from "@/lib/api";
import { isCode, messageFor } from "@/lib/errors";
import { km, motoName } from "@/lib/format";
import { useSession } from "@/lib/session";
import { invalidate, useQuery } from "@/lib/use-query";
import { personalDataWarning } from "@/lib/public-text";
import { Button } from "@/components/ui/button";
import { Input, Textarea } from "@/components/ui/field";
import { Banner, Card, CardHeader, Dialog, EmptyState, ErrorBanner, PageHeader } from "@/components/ui/primitives";
import { PlateTag } from "@/components/nitida/status";
import { EvidenceSummary, HistoryTimeline, SealSkeleton, TimelineSkeleton, VerificationSeal, sealDetail } from "@/components/nitida/history";
import { ShareLinkDialog } from "@/components/nitida/share-link";
import { PhotoInput, sampleUpload, type UploadedPhoto } from "@/components/nitida/photo-input";
import { BrokenLinkFigure, EmptyHistoryFigure } from "@/components/illustrations";
import type { HistoryView } from "@/lib/types";

export function MotoHistory() {
  const { id } = useParams<{ id: string }>();
  const { can } = useSession();
  const h = useQuery<HistoryView>(`history:${id}`, () => api.get(`/motorcycles/${id}/history`));
  const [share, setShare] = useState(false);
  const [amendSeq, setAmendSeq] = useState<number | null>(null);
  const [odometer, setOdometer] = useState(false);

  if (h.error) {
    return isCode(h.error, "NOT_FOUND") ? (
      <EmptyState illustration={<BrokenLinkFigure />} title="No encontramos esta moto" line="Solo se ve el historial de las motos que este taller ha recibido." action={<Link href="/recepcion" className="font-medium text-primary">Ir a Recepción</Link>} />
    ) : <ErrorBanner error={h.error} onRetry={h.refetch} />;
  }
  const d = h.data;
  const last = d?.entries.filter((e) => typeof e.content.mileage === "number").at(-1)?.content.mileage as number | undefined;

  return (
    <>
      <PageHeader
        back={<Link href="/ordenes" className="mb-2 inline-flex items-center gap-1 text-body-sm text-ink-muted hover:text-ink"><ArrowLeft aria-hidden className="size-4" /> Volver</Link>}
        title={<span className="flex flex-wrap items-center gap-3">{d ? motoName(d.motorcycle) : "Historial"}{d && <PlateTag plate={d.motorcycle.plate} />}</span>}
        subtitle={d ? [d.motorcycle.modelYear && `Modelo ${d.motorcycle.modelYear}`, d.motorcycle.displacementCc && `${d.motorcycle.displacementCc} cc`, last != null && `último registro: ${km(last)}`].filter(Boolean).join(" · ") : "Cargando…"}
        action={
          <>
            {can("WORKSHOP_OWNER", "MECHANIC") && <Button icon={Gauge} onClick={() => setOdometer(true)}>Cambio de odómetro</Button>}
            {can("WORKSHOP_OWNER", "FRONT_DESK") && <Button variant="primary" icon={Share2} onClick={() => setShare(true)}>Compartir historial</Button>}
          </>
        }
      />
      <div className="grid grid-cols-1 gap-4 lg:grid-cols-[minmax(0,1fr)_320px] lg:gap-6">
        <Card className="min-w-0">
          <CardHeader title="Línea de tiempo" subtitle="Cada registro se encadena con el anterior y se firma. Nada se borra: las correcciones se agregan." />
          <div className="px-4 pb-5 lg:px-5">
            {!d ? <TimelineSkeleton /> : d.entries.length === 0 ? (
              <EmptyState illustration={<EmptyHistoryFigure />} title="Esta moto empieza aquí" line="Su primer servicio en Nítida quedará firmado en este historial." />
            ) : (
              <HistoryTimeline history={d} onAmend={can("WORKSHOP_OWNER", "MECHANIC") ? setAmendSeq : undefined} />
            )}
          </div>
        </Card>
        <aside className="flex flex-col gap-4">
          <Card className="p-5">
            {d ? <VerificationSeal verification={d.verification} size="md" detail={sealDetail(d)} audience="workshop" /> : <SealSkeleton size="md" />}
            {d && <div className="mt-5"><EvidenceSummary history={d} /></div>}
          </Card>
          <Card className="p-5 text-body-sm text-ink-muted">
            <h2 className="mb-2 text-body-sm font-semibold text-ink">Qué garantiza el sello</h2>
            <p>Que ningún registro se alteró ni se borró después de firmarse, y qué taller registró qué y cuándo dentro de Nítida.</p>
            <p className="mt-2">No certifica que un repuesto sea original: eso lo indica la evidencia de cada repuesto (escaneado, serial o sello revisado).</p>
          </Card>
        </aside>
      </div>
      <ShareLinkDialog open={share} onClose={() => setShare(false)} endpoint={`/motorcycles/${id}/links`} />
      <AmendDialog seq={amendSeq} motoId={id} onClose={() => setAmendSeq(null)} />
      <OdometerDialog open={odometer} motoId={id} lastKm={last} onClose={() => setOdometer(false)} />
    </>
  );
}

function AmendDialog({ seq, motoId, onClose }: { seq: number | null; motoId: string; onClose: () => void }) {
  const [reason, setReason] = useState("");
  const [mileage, setMileage] = useState("");
  const [diagnosis, setDiagnosis] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  return (
    <Dialog open={seq != null} onClose={onClose} title={`Corregir el registro #${seq}`}
      footer={<><Button variant="ghost" onClick={onClose}>Volver</Button>
        <Button variant="primary" icon={FilePen} loading={busy} loadingText="Firmando…" onClick={async () => {
          if (reason.trim().length < 10) return setError("Explique el motivo con al menos 10 caracteres.");
          if (personalDataWarning(reason) || personalDataWarning(diagnosis)) return setError("Quite los datos personales: la corrección queda pública en el historial.");
          setBusy(true);
          setError(null);
          try {
            await api.post(`/motorcycles/${motoId}/history/amendments`, {
              amendsSeq: seq, reason: reason.trim(), mileage: mileage ? Number(mileage) : undefined, diagnosis: diagnosis.trim() || undefined,
            });
            invalidate(`history:${motoId}`);
            toast.success("Corrección firmada");
            setReason(""); setMileage(""); setDiagnosis("");
            onClose();
          } catch (e) {
            setError(messageFor(e));
          } finally {
            setBusy(false);
          }
        }}>Firmar corrección</Button></>}>
      <div className="flex flex-col gap-4 text-ink">
        <p className="text-body-sm text-ink-muted">El registro original no cambia ni se oculta. La corrección se agrega como un registro nuevo, firmado y enlazado.</p>
        <Input label="Motivo" placeholder="El kilometraje se digitó con un cero de más" value={reason} onChange={(e) => setReason(e.target.value)} hint="Entre 10 y 500 caracteres. Queda público en el historial." warning={personalDataWarning(reason)} />
        <Input label="Kilometraje correcto" optional inputMode="numeric" suffix="km" value={mileage} onChange={(e) => setMileage(e.target.value.replace(/\D/g, "").slice(0, 6))} />
        <Textarea label="Diagnóstico correcto" optional value={diagnosis} onChange={(e) => setDiagnosis(e.target.value)} warning={personalDataWarning(diagnosis)} />
        {error && <Banner tone="danger">{error}</Banner>}
      </div>
    </Dialog>
  );
}

function OdometerDialog({ open, motoId, lastKm, onClose }: { open: boolean; motoId: string; lastKm?: number; onClose: () => void }) {
  const { demo } = useSession();
  const [mileage, setMileage] = useState("");
  const [reason, setReason] = useState("");
  const [photo, setPhoto] = useState<UploadedPhoto | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  return (
    <Dialog open={open} onClose={onClose} title="Registrar cambio de odómetro"
      footer={<><Button variant="ghost" onClick={onClose}>Volver</Button>
        <Button variant="primary" icon={Gauge} loading={busy} loadingText="Firmando…" onClick={async () => {
          if (reason.trim().length < 10) return setError("Explique el motivo con al menos 10 caracteres.");
          if (personalDataWarning(reason)) return setError("Quite los datos personales: el motivo queda público en el historial.");
          if (!photo) return setError("Tome la foto del tablero nuevo.");
          setBusy(true);
          setError(null);
          try {
            await api.post(`/motorcycles/${motoId}/history/odometer-changes`, { mileage: Number(mileage), reason: reason.trim(), photoId: photo.fileId });
            invalidate(`history:${motoId}`);
            toast.success("Cambio de odómetro firmado");
            onClose();
          } catch (e) {
            setError(messageFor(e));
          } finally {
            setBusy(false);
          }
        }}>Firmar cambio</Button></>}>
      <div className="flex flex-col gap-4 text-ink">
        <p className="text-body-sm text-ink-muted">Úselo cuando cambian el tablero o el odómetro y el kilometraje vuelve a empezar{lastKm != null ? ` (el último registrado es ${km(lastKm)})` : ""}. Queda visible en el historial público.</p>
        <Input label="Kilometraje del tablero nuevo" inputMode="numeric" suffix="km" placeholder="0" value={mileage} onChange={(e) => setMileage(e.target.value.replace(/\D/g, "").slice(0, 6))} />
        <Input label="Motivo" placeholder="Cambio de tablero por daño en el velocímetro" value={reason} onChange={(e) => setReason(e.target.value)} hint="Entre 10 y 500 caracteres. Queda público en el historial." warning={personalDataWarning(reason)} />
        <PhotoInput label="Foto del tablero nuevo" hint="Que se lea el kilometraje." value={photo} onChange={setPhoto} />
        {demo && !photo && <Button variant="ghost" size="sm" icon={ImagePlus} className="self-start" onClick={async () => setPhoto(await sampleUpload("odometro"))}>Usar foto de ejemplo (demostración)</Button>}
        {error && <Banner tone="danger">{error}</Banner>}
      </div>
    </Dialog>
  );
}
