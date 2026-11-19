"use client";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { AnimatePresence, motion } from "motion/react";
import {
  Bike, Building2, ClipboardList, Ellipsis, House, Inbox, LogOut, RotateCcw, Search, Settings, ShieldCheck, Users, Wallet, WifiOff,
  type LucideIcon, ChevronsUpDown, Check, UserRound,
} from "lucide-react";
import { useEffect, useState, type ReactNode } from "react";
import { Lockup, Mark } from "@/components/brand/logo";
import { Banner, Dialog } from "@/components/ui/primitives";
import { Button } from "@/components/ui/button";
import { CommandPalette } from "./command-palette";
import { ThemeToggle } from "./app-shell-theme";
import { useSession } from "@/lib/session";
import { ROLE_LABEL } from "@/lib/format";
import type { Role } from "@/lib/types";

interface NavItem { href: string; label: string; icon: LucideIcon; roles?: Role[]; admin?: boolean; any?: boolean }

const NAV: NavItem[] = [
  { href: "/inicio", label: "Inicio", icon: House, roles: ["WORKSHOP_OWNER", "MECHANIC", "FRONT_DESK"] },
  { href: "/recepcion", label: "Recepción", icon: Inbox, roles: ["WORKSHOP_OWNER", "MECHANIC", "FRONT_DESK"] },
  { href: "/ordenes", label: "Órdenes", icon: ClipboardList, roles: ["WORKSHOP_OWNER", "MECHANIC", "FRONT_DESK"] },
  { href: "/caja", label: "Caja y alertas", icon: Wallet, roles: ["WORKSHOP_OWNER"] },
  { href: "/equipo", label: "Equipo", icon: Users, roles: ["WORKSHOP_OWNER"] },
  { href: "/ajustes", label: "Ajustes del taller", icon: Settings, roles: ["WORKSHOP_OWNER"] },
  { href: "/mis-motos", label: "Mis motos", icon: Bike, any: true },
  { href: "/cuenta", label: "Mi cuenta", icon: UserRound, any: true },
  { href: "/admin", label: "Talleres", icon: Building2, admin: true },
];

function useNav() {
  const { role, me } = useSession();
  return NAV.filter((n) => (n.admin ? me.platformAdmin : n.any ? true : role != null && n.roles!.includes(role)));
}

function isActive(pathname: string, href: string) {
  return pathname === href || pathname.startsWith(`${href}/`) || (href === "/ordenes" && pathname.startsWith("/motos"));
}

export function AppShell({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const nav = useNav();
  const { workshop, demo } = useSession();
  const [paletteOpen, setPaletteOpen] = useState(false);
  const [moreOpen, setMoreOpen] = useState(false);
  const online = useOnline();

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === "k") {
        e.preventDefault();
        setPaletteOpen((o) => !o);
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, []);

  const mobileMain = nav.slice(0, 3);
  const mobileMore = nav.slice(3);

  return (
    <div className="min-h-screen lg:grid lg:grid-cols-[240px_1fr]">
      <a href="#contenido" className="sr-only focus:not-sr-only focus:fixed focus:top-2 focus:left-2 focus:z-50 focus:rounded-md focus:bg-surface focus:px-3 focus:py-2">
        Saltar al contenido
      </a>
      {/* Barra lateral (escritorio) */}
      <aside className="sticky top-0 hidden h-screen flex-col border-r border-border bg-surface lg:flex">
        <div className="flex h-16 items-center px-5">
          <Link href={nav[0]?.href ?? "/"} aria-label="Nítida, inicio"><Lockup size={28} /></Link>
        </div>
        <WorkshopSwitcher />
        <nav aria-label="Principal" className="mt-2 flex flex-1 flex-col gap-0.5 px-3">
          {nav.map((n) => {
            const active = isActive(pathname, n.href);
            return (
              <Link
                key={n.href}
                href={n.href}
                aria-current={active ? "page" : undefined}
                className={`relative flex h-10 items-center gap-3 rounded-md px-3 text-body-sm font-medium transition-colors duration-[120ms] ${
                  active ? "text-primary-soft-ink" : "text-ink-muted hover:bg-surface-sunken hover:text-ink"
                }`}
              >
                {active && (
                  <motion.span layoutId="nav-active" className="absolute inset-0 rounded-md bg-primary-soft" transition={{ duration: 0.18, ease: [0.2, 0.8, 0.2, 1] }} />
                )}
                <n.icon aria-hidden className="relative size-[18px]" strokeWidth={1.75} />
                <span className="relative">{n.label}</span>
              </Link>
            );
          })}
        </nav>
        <div className="border-t border-border p-3">
          <button
            type="button"
            onClick={() => setPaletteOpen(true)}
            className="mb-2 flex h-10 w-full items-center gap-2 rounded-md border border-border bg-canvas px-3 text-body-sm text-ink-muted hover:border-border-strong"
          >
            <Search aria-hidden className="size-4" strokeWidth={1.75} />
            Buscar placa u orden
            <kbd className="ml-auto rounded-xs border border-border bg-surface px-1.5 font-mono text-caption">Ctrl K</kbd>
          </button>
          <UserBlock />
        </div>
      </aside>

      <div className="flex min-w-0 flex-col">
        {/* Barra superior (móvil) */}
        <header className="sticky top-0 z-20 flex h-14 items-center justify-between border-b border-border bg-surface px-4 lg:hidden">
          <Link href={nav[0]?.href ?? "/"} className="flex items-center gap-2" aria-label="Nítida, inicio">
            <Mark size={28} />
            <span className="max-w-[180px] truncate text-body-sm font-medium">{workshop?.name ?? "Nítida"}</span>
          </Link>
          <div className="flex items-center">
            <button type="button" aria-label="Buscar placa u orden" onClick={() => setPaletteOpen(true)} className="flex size-12 items-center justify-center rounded-md text-ink-muted hover:bg-surface-sunken">
              <Search aria-hidden className="size-5" strokeWidth={1.75} />
            </button>
            <ThemeToggle />
          </div>
        </header>

        <AnimatePresence>
          {!online && (
            <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} role="status" className="flex items-center justify-center gap-2 bg-attention-soft px-4 py-1.5 text-caption text-attention-ink">
              <WifiOff aria-hidden className="size-4" strokeWidth={1.75} />
              Sin conexión. Los cambios se guardarán cuando vuelva la señal.
            </motion.div>
          )}
        </AnimatePresence>
        {demo && <DemoBar />}

        <main id="contenido" className="mx-auto w-full max-w-[1200px] flex-1 px-4 pt-6 pb-28 lg:px-8 lg:pt-8 lg:pb-12">
          <WorkshopNotices />
          {children}
        </main>
      </div>

      {/* Navegación inferior (móvil): máximo 4 destinos */}
      <nav aria-label="Principal" className="fixed inset-x-0 bottom-0 z-30 grid border-t border-border bg-surface pb-safe lg:hidden" style={{ gridTemplateColumns: `repeat(${Math.min(nav.length, 4)}, 1fr)` }}>
        {(mobileMore.length ? mobileMain : nav).map((n) => {
          const active = isActive(pathname, n.href);
          return (
            <Link key={n.href} href={n.href} aria-current={active ? "page" : undefined} className={`flex min-h-14 flex-col items-center justify-center gap-0.5 pt-1.5 text-caption ${active ? "text-primary font-medium" : "text-ink-muted"}`}>
              <n.icon aria-hidden className="size-5" strokeWidth={1.75} />
              {n.label.split(" ")[0]}
            </Link>
          );
        })}
        {mobileMore.length > 0 && (
          <button type="button" onClick={() => setMoreOpen(true)} className={`flex min-h-14 flex-col items-center justify-center gap-0.5 pt-1.5 text-caption ${mobileMore.some((n) => isActive(pathname, n.href)) ? "text-primary font-medium" : "text-ink-muted"}`}>
            <Ellipsis aria-hidden className="size-5" strokeWidth={1.75} />
            Más
          </button>
        )}
      </nav>
      <Dialog open={moreOpen} onClose={() => setMoreOpen(false)} title="Más opciones">
        <div className="-mx-2 flex flex-col">
          {mobileMore.map((n) => (
            <Link key={n.href} href={n.href} onClick={() => setMoreOpen(false)} className="flex h-12 items-center gap-3 rounded-md px-2 text-body text-ink hover:bg-surface-sunken">
              <n.icon aria-hidden className="size-5 text-ink-muted" strokeWidth={1.75} />
              {n.label}
            </Link>
          ))}
          <div className="mt-2 border-t border-border pt-3"><UserBlock /></div>
        </div>
      </Dialog>

      <CommandPalette open={paletteOpen} onClose={() => setPaletteOpen(false)} nav={nav} />
    </div>
  );
}

function useOnline() {
  const [online, setOnline] = useState(true);
  useEffect(() => {
    const up = () => setOnline(true);
    const down = () => setOnline(false);
    window.addEventListener("online", up);
    window.addEventListener("offline", down);
    return () => {
      window.removeEventListener("online", up);
      window.removeEventListener("offline", down);
    };
  }, []);
  return online;
}

function DemoBar() {
  const [confirm, setConfirm] = useState(false);
  return (
    <div className="flex items-center justify-center gap-3 border-b border-border bg-surface-sunken px-4 py-1.5 text-caption text-ink-muted">
      <span>Modo demostración: los datos son ficticios y viven solo en este navegador.</span>
      <button type="button" onClick={() => setConfirm(true)} className="inline-flex items-center gap-1 font-medium text-primary hover:underline">
        <RotateCcw aria-hidden className="size-3.5" strokeWidth={1.75} /> Reiniciar datos
      </button>
      <Dialog
        open={confirm}
        onClose={() => setConfirm(false)}
        title="¿Reiniciar los datos de demostración?"
        destructive
        footer={
          <>
            <Button variant="ghost" data-autofocus onClick={() => setConfirm(false)}>Volver</Button>
            <Button
              variant="danger"
              icon={RotateCcw}
              onClick={async () => {
                const { resetDemo } = await import("@/lib/demo/server");
                resetDemo();
                window.location.reload();
              }}
            >
              Reiniciar datos
            </Button>
          </>
        }
      >
        Se borran las recepciones, órdenes y pagos que haya creado y vuelven los datos iniciales del taller de ejemplo.
      </Dialog>
    </div>
  );
}

function WorkshopNotices() {
  const { workshop, role, me } = useSession();
  const pathname = usePathname();
  if (me.totpSetupRequired && pathname !== "/cuenta" && pathname !== "/admin")
    return (
      <div className="mb-6">
        <Banner tone="attention" title="Active la verificación en dos pasos" action={<Link href="/cuenta#seguridad" className="inline-flex h-10 items-center rounded-md px-3 text-body-sm font-medium underline underline-offset-2">Activarla</Link>}>
          Es obligatoria para administrar la plataforma. Hasta activarla puede ingresar, pero no verificar ni suspender talleres.
        </Banner>
      </div>
    );
  if (!workshop) return null;
  if (workshop.status === "SUSPENDED")
    return <div className="mb-6"><Banner tone="danger" title="Su taller está suspendido">Puede consultar la información, pero no hacer cambios. Escriba a soporte para reactivarlo.</Banner></div>;
  if (workshop.status === "PENDING_VERIFICATION" && role === "WORKSHOP_OWNER")
    return (
      <div className="mb-6">
        <Banner tone="attention" title="Taller pendiente de verificación">
          Puede trabajar normalmente. Mientras Nítida verifica sus documentos, los registros del historial se marcan como “taller sin verificar”.
        </Banner>
      </div>
    );
  return null;
}

function WorkshopSwitcher() {
  const { workshops, workshop, selectWorkshop, role } = useSession();
  const [open, setOpen] = useState(false);
  const router = useRouter();
  if (!workshop) return null;
  return (
    <div className="relative px-3">
      <button
        type="button"
        onClick={() => workshops.length > 1 && setOpen((o) => !o)}
        aria-expanded={open}
        className="flex w-full items-center gap-3 rounded-md border border-border px-3 py-2 text-left hover:bg-surface-sunken"
      >
        <span className="flex size-8 shrink-0 items-center justify-center rounded-sm bg-surface-sunken text-caption font-semibold text-ink-muted">
          {workshop.name.split(" ").map((w) => w[0]).slice(0, 2).join("")}
        </span>
        <span className="min-w-0 flex-1">
          <span className="block truncate text-body-sm font-medium text-ink">{workshop.name}</span>
          <span className="flex items-center gap-1 text-caption text-ink-muted">
            {workshop.status === "VERIFIED" && <ShieldCheck aria-label="Taller verificado" className="size-3.5 text-verified" strokeWidth={1.75} />}
            {role ? ROLE_LABEL[role] : ""} · {workshop.municipality}
          </span>
        </span>
        {workshops.length > 1 && <ChevronsUpDown aria-hidden className="size-4 text-ink-muted" strokeWidth={1.75} />}
      </button>
      {open && (
        <div className="absolute inset-x-3 top-full z-30 mt-1 rounded-md border border-border bg-surface p-1 shadow-raised">
          {workshops.map((w) => (
            <button
              key={w.id}
              type="button"
              onClick={() => {
                selectWorkshop(w.id);
                setOpen(false);
                router.push("/inicio");
              }}
              className="flex h-10 w-full items-center justify-between rounded-sm px-3 text-body-sm hover:bg-surface-sunken"
            >
              {w.name}
              {w.id === workshop.id && <Check aria-hidden className="size-4 text-primary" />}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}

function UserBlock() {
  const { me, logout } = useSession();
  return (
    <div className="flex items-center gap-2">
      <span className="flex size-9 shrink-0 items-center justify-center rounded-full bg-primary-soft text-caption font-semibold text-primary-soft-ink" aria-hidden>
        {me.displayName.split(" ").map((w) => w[0]).slice(0, 2).join("")}
      </span>
      <span className="min-w-0 flex-1">
        <span className="block truncate text-body-sm font-medium text-ink">{me.displayName}</span>
        <span className="block truncate text-caption text-ink-muted">{me.email}</span>
      </span>
      <span className="hidden lg:block"><ThemeToggle /></span>
      <button type="button" aria-label="Cerrar sesión" title="Cerrar sesión" onClick={logout} className="flex size-12 items-center justify-center rounded-md text-ink-muted hover:bg-surface-sunken hover:text-ink lg:size-10">
        <LogOut aria-hidden className="size-[18px]" strokeWidth={1.75} />
      </button>
    </div>
  );
}
