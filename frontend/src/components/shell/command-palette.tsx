"use client";
import { Command } from "cmdk";
import { useRouter } from "next/navigation";
import { Bike, ClipboardList, LoaderCircle, Search, type LucideIcon } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { api } from "@/lib/api";
import { messageFor } from "@/lib/errors";
import { normalizePlate, plate as fmtPlate, shortId } from "@/lib/format";
import { useSession } from "@/lib/session";
import { useQuery } from "@/lib/use-query";
import { ORDER_STATUS } from "@/components/nitida/status";
import type { MotorcycleInfo, OrderSummary } from "@/lib/types";

const PLATE_RE = /^[A-Z]{3}\s?\d{2}[A-Z]?$/i;

/** Paleta de comandos (Ctrl/Cmd + K): ir a una pantalla, buscar una placa o una orden. */
export function CommandPalette({ open, onClose, nav }: { open: boolean; onClose: () => void; nav: { href: string; label: string; icon: LucideIcon }[] }) {
  const router = useRouter();
  const { workshop } = useSession();
  const ref = useRef<HTMLDialogElement>(null);
  const [q, setQ] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const orders = useQuery<OrderSummary[]>(open && workshop ? "orders:palette" : null, () => api.get("/orders?limit=100"));

  useEffect(() => {
    const d = ref.current;
    if (!d) return;
    if (open && !d.open) {
      d.showModal();
      d.querySelector("input")?.focus();
    }
    if (!open && d.open) d.close();
  }, [open]);

  const go = (href: string) => {
    onClose();
    setQ("");
    setError(null);
    router.push(href);
  };

  const lookup = async () => {
    setBusy(true);
    setError(null);
    try {
      const m = await api.get<MotorcycleInfo>(`/motorcycles/lookup?plate=${encodeURIComponent(normalizePlate(q))}`);
      go(`/motos/${m.id}`);
    } catch (e) {
      setError(messageFor(e) === "No encontramos lo que buscaba." ? `No encontramos ${fmtPlate(q)}. Revise la placa o regístrela en Recepción.` : messageFor(e));
    } finally {
      setBusy(false);
    }
  };

  const looksLikePlate = PLATE_RE.test(q.trim());
  const ql = q.trim().toLowerCase();
  const matchingOrders = ql.length >= 2 ? (orders.data ?? []).filter((o) => o.diagnosis.toLowerCase().includes(ql) || shortId(o.id).toLowerCase().includes(ql)).slice(0, 6) : [];

  return (
    <dialog
      ref={ref}
      onClose={onClose}
      onClick={(e) => e.target === ref.current && onClose()}
      className="m-auto mt-[12vh] w-[calc(100%-32px)] max-w-[560px] rounded-lg bg-surface p-0 text-ink shadow-overlay"
      aria-label="Buscar"
    >
      <Command label="Buscar en Nítida" shouldFilter={false} className="flex flex-col">
        <div className="flex items-center gap-2 border-b border-border px-4">
          <Search aria-hidden className="size-5 text-ink-muted" strokeWidth={1.75} />
          <Command.Input
            value={q}
            onValueChange={(v) => {
              setQ(v);
              setError(null);
            }}
            placeholder="Placa (ABC 12D), diagnóstico o pantalla"
            className="h-14 flex-1 bg-transparent text-body outline-none placeholder:text-ink-subtle"
            autoFocus
          />
          {busy && <LoaderCircle aria-hidden className="size-4 animate-spin text-ink-muted" />}
        </div>
        <Command.List className="max-h-[360px] overflow-y-auto p-2">
          {error && <p role="alert" className="px-3 py-3 text-body-sm text-danger-ink">{error}</p>}
          {looksLikePlate && workshop && (
            <Command.Group heading="Moto" className="text-caption text-ink-muted [&_[cmdk-group-heading]]:px-3 [&_[cmdk-group-heading]]:py-1.5">
              <Item icon={Bike} onSelect={lookup}>Buscar la moto <span className="font-mono font-medium">{fmtPlate(q)}</span></Item>
            </Command.Group>
          )}
          {matchingOrders.length > 0 && (
            <Command.Group heading="Órdenes" className="text-caption text-ink-muted [&_[cmdk-group-heading]]:px-3 [&_[cmdk-group-heading]]:py-1.5">
              {matchingOrders.map((o) => (
                <Item key={o.id} icon={ClipboardList} onSelect={() => go(`/ordenes/${o.id}`)}>
                  <span className="truncate">#{shortId(o.id)} · {o.diagnosis}</span>
                  <span className="ml-auto shrink-0 text-caption text-ink-muted">{ORDER_STATUS[o.status].label}</span>
                </Item>
              ))}
            </Command.Group>
          )}
          <Command.Group heading="Ir a" className="text-caption text-ink-muted [&_[cmdk-group-heading]]:px-3 [&_[cmdk-group-heading]]:py-1.5">
            {nav.filter((n) => !ql || n.label.toLowerCase().includes(ql)).map((n) => (
              <Item key={n.href} icon={n.icon} onSelect={() => go(n.href)}>{n.label}</Item>
            ))}
          </Command.Group>
          <Command.Empty className="px-3 py-6 text-center text-body-sm text-ink-muted">Sin resultados. Para buscar una moto escriba la placa completa.</Command.Empty>
        </Command.List>
        <div className="flex gap-4 border-t border-border px-4 py-2 text-caption text-ink-muted">
          <span><kbd className="font-mono">↑↓</kbd> moverse</span>
          <span><kbd className="font-mono">Enter</kbd> abrir</span>
          <span><kbd className="font-mono">Esc</kbd> cerrar</span>
        </div>
      </Command>
    </dialog>
  );
}

function Item({ icon: Icon, children, onSelect }: { icon: LucideIcon; children: React.ReactNode; onSelect: () => void }) {
  return (
    <Command.Item
      onSelect={onSelect}
      className="flex h-11 cursor-pointer items-center gap-3 rounded-md px-3 text-body-sm text-ink data-[selected=true]:bg-primary-soft data-[selected=true]:text-primary-soft-ink"
    >
      <Icon aria-hidden className="size-4 shrink-0" strokeWidth={1.75} />
      {children}
    </Command.Item>
  );
}
