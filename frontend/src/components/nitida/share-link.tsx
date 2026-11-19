"use client";
import { Link2, MessageCircle, Share2, Unlink } from "lucide-react";
import { useState } from "react";
import { toast } from "sonner";
import { api } from "@/lib/api";
import { messageFor } from "@/lib/errors";
import { date } from "@/lib/format";
import { Button } from "@/components/ui/button";
import { Choice } from "@/components/ui/field";
import { Banner, CopyButton, Dialog } from "@/components/ui/primitives";
import type { PublicLink } from "@/lib/types";

/** Crea un enlace público de solo lectura. El token se muestra una vez; se puede revocar. */
export function ShareLinkDialog({ open, onClose, endpoint, revocable = true }: { open: boolean; onClose: () => void; endpoint: string; revocable?: boolean }) {
  const [days, setDays] = useState<"7" | "30" | "90" | "365">("30");
  const [link, setLink] = useState<PublicLink | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const url = link ? `${window.location.origin}/v/${link.token}` : "";

  const close = () => {
    setLink(null);
    setError(null);
    onClose();
  };

  return (
    <Dialog open={open} onClose={close} title={link ? "Enlace listo para compartir" : "Compartir historial"}
      footer={link ? (
        <>
          {revocable ? (
            <Button variant="ghost" icon={Unlink} onClick={async () => {
              try {
                await api.del(`/links/${link.linkId}`);
                toast.success("Enlace revocado");
                close();
              } catch (e) {
                setError(messageFor(e));
              }
            }}>Revocar enlace</Button>
          ) : <span />}
          <a href={`https://wa.me/?text=${encodeURIComponent(`Historial verificado de la moto en Nítida: ${url}`)}`} target="_blank" rel="noopener noreferrer"
            className="inline-flex h-12 items-center justify-center gap-2 rounded-md bg-primary px-4 text-body-sm font-medium text-on-primary hover:bg-primary-hover lg:h-10">
            <MessageCircle aria-hidden className="size-[18px]" strokeWidth={1.75} /> Enviar por WhatsApp
          </a>
        </>
      ) : (
        <>
          <Button variant="ghost" onClick={close}>Volver</Button>
          <Button variant="primary" icon={Link2} loading={busy} loadingText="Creando…" onClick={async () => {
            setBusy(true);
            setError(null);
            try {
              setLink(await api.post<PublicLink>(endpoint, { days: Number(days) }));
            } catch (e) {
              setError(messageFor(e));
            } finally {
              setBusy(false);
            }
          }}>Crear enlace</Button>
        </>
      )}>
      {!link ? (
        <div className="flex flex-col gap-4 text-ink">
          <p className="text-body-sm text-ink-muted">Quien abra el enlace ve los servicios, el kilometraje y la verificación de la cadena. No ve precios, nombres ni la placa completa.</p>
          <Choice label="Vigencia" value={days} onChange={setDays} columns={4}
            options={[{ value: "7", label: "7 días" }, { value: "30", label: "30 días" }, { value: "90", label: "90 días" }, { value: "365", label: "1 año" }]} />
          {error && <Banner tone="danger">{error}</Banner>}
        </div>
      ) : (
        <div className="flex flex-col gap-3 text-ink">
          <div className="flex items-center gap-2 rounded-md border border-border bg-surface-sunken p-3">
            <Share2 aria-hidden className="size-4 shrink-0 text-ink-muted" />
            <a href={url} target="_blank" rel="noopener noreferrer" className="min-w-0 flex-1 truncate font-mono text-caption text-primary">{url}</a>
            <CopyButton value={url} label="Copiar enlace" />
          </div>
          <p className="text-caption text-ink-muted">Vence el {date(link.expiresAt)}. Guárdelo ahora: por seguridad no lo volveremos a mostrar.</p>
          {error && <Banner tone="danger">{error}</Banner>}
        </div>
      )}
    </Dialog>
  );
}
