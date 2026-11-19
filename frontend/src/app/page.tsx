import Link from "next/link";
import { ArrowRight, Check, LogIn, Minus } from "lucide-react";
import { Lockup } from "@/components/brand/logo";
import { ChainHero } from "@/components/illustrations";
import { ChainPlayground } from "@/components/landing/chain-playground";
import { FlowExplorer } from "@/components/landing/flow-explorer";
import { ThemeToggle } from "@/components/shell/app-shell-theme";

const ROLES = [
  { role: "Dueño del taller", does: "Ve el tablero, cuadra la caja, revisa las alertas, invita al equipo y ajusta cuándo avisar.", sees: "Todo lo del taller" },
  { role: "Recepción", does: "Registra la entrada de la moto, crea clientes, cobra, entrega y comparte historiales.", sees: "Órdenes y pagos" },
  { role: "Mecánico", does: "Abre y trabaja las órdenes, registra repuestos con su evidencia y corrige el historial.", sees: "Órdenes, sin dinero" },
  { role: "Dueño de la moto", does: "Agrega su moto con el código del recibo, ve su historial y lo comparte o traspasa al venderla.", sees: "Solo sus motos" },
];

const GUARANTEES = [
  "Que ningún registro se cambió ni se borró después de firmarse.",
  "Qué taller registró cada servicio y cuándo, dentro de Nítida.",
  "Que el kilometraje no retrocede sin un cambio de odómetro declarado y con foto.",
];
const LIMITS = [
  "Que un repuesto sea original: escanear un código es evidencia, no prueba.",
  "Que el taller dijo la verdad al registrar: firma lo que se registró, no lo que pasó.",
  "No-repudio frente a la propia plataforma: Nítida guarda las llaves cifradas en su servidor.",
];

export default function Landing() {
  return (
    <div className="min-h-screen">
      <header className="sticky top-0 z-30 border-b border-border bg-canvas/95">
        <div className="mx-auto flex h-16 max-w-[1200px] items-center justify-between px-4 lg:px-8">
          <Link href="/" aria-label="Nítida, portada"><Lockup size={30} animate /></Link>
          <nav aria-label="Secciones" className="hidden items-center gap-6 text-body-sm text-ink-muted md:flex">
            <a href="#flujo" className="hover:text-ink">Cómo funciona</a>
            <a href="#cadena" className="hover:text-ink">Pruebe la cadena</a>
            <a href="#roles" className="hover:text-ink">Para quién</a>
          </nav>
          <div className="flex items-center gap-1">
            <ThemeToggle />
            <Link href="/login" className="inline-flex h-10 items-center gap-2 rounded-md bg-primary px-4 text-body-sm font-medium text-on-primary hover:bg-primary-hover">
              <LogIn aria-hidden className="size-[18px]" strokeWidth={1.75} /> Ingresar
            </Link>
          </div>
        </div>
      </header>

      <main>
        <section className="mx-auto grid grid-cols-1 max-w-[1200px] items-center gap-10 px-4 pt-12 pb-16 lg:grid-cols-2 lg:px-8 lg:pt-20 lg:pb-24">
          <div>
            <p className="text-body-sm font-medium text-primary">Para talleres de motos en Colombia</p>
            <h1 className="mt-3 text-hero font-semibold text-ink lg:text-hero-xl">El historial de cada moto, firmado y verificable.</h1>
            <p className="mt-5 max-w-[52ch] text-body text-ink-muted">
              Nítida lleva la recepción, las órdenes de trabajo, los pagos y la caja del taller. Cada servicio entregado queda encadenado al anterior y firmado, y el dueño de la moto lo comparte con un enlace cuando la vende.
            </p>
            <div className="mt-8 flex flex-col gap-3 sm:flex-row">
              <Link href="/login" className="inline-flex h-12 items-center justify-center gap-2 rounded-md bg-primary px-5 text-body-sm font-medium text-on-primary hover:bg-primary-hover">
                Entrar al taller de ejemplo <ArrowRight aria-hidden className="size-[18px]" strokeWidth={1.75} />
              </Link>
              <a href="#cadena" className="inline-flex h-12 items-center justify-center rounded-md border border-border-strong bg-surface px-5 text-body-sm font-medium text-ink hover:bg-surface-sunken">
                Ver cómo se verifica
              </a>
            </div>
            <dl className="mt-10 grid max-w-md grid-cols-3 gap-4 border-t border-border pt-6">
              <div><dt className="text-caption text-ink-muted">Huella</dt><dd className="font-mono text-body-sm font-medium text-ink">SHA-256</dd></div>
              <div><dt className="text-caption text-ink-muted">Firma</dt><dd className="font-mono text-body-sm font-medium text-ink">Ed25519</dd></div>
              <div><dt className="text-caption text-ink-muted">Datos</dt><dd className="text-body-sm font-medium text-ink">Ley 1581</dd></div>
            </dl>
          </div>
          <div className="rounded-xl border border-border bg-surface-sunken p-6 lg:p-10">
            <ChainHero className="w-full" />
          </div>
        </section>

        <section id="flujo" className="scroll-mt-16 border-y border-border bg-surface-sunken">
          <div className="mx-auto max-w-[1200px] px-4 py-16 lg:px-8 lg:py-24">
            <h2 className="text-display font-semibold text-ink">Del taller a la hoja pública</h2>
            <p className="mt-2 mb-10 max-w-[60ch] text-body text-ink-muted">Cuatro momentos, cada uno a cargo de una persona del taller. Toque cada paso para ver qué queda registrado.</p>
            <FlowExplorer />
          </div>
        </section>

        <section id="cadena" className="scroll-mt-16 mx-auto max-w-[1200px] px-4 py-16 lg:px-8 lg:py-24">
          <h2 className="text-display font-semibold text-ink">Pruebe la cadena: cambie un dato</h2>
          <p className="mt-2 mb-10 max-w-[64ch] text-body text-ink-muted">
            Estos tres registros ya se firmaron. Edite el kilometraje o el trabajo de cualquiera y mire qué pasa con la verificación. Así es como Nítida detecta un historial alterado.
          </p>
          <ChainPlayground />
        </section>

        <section className="border-y border-border bg-surface">
          <div className="mx-auto grid grid-cols-1 max-w-[1200px] gap-10 px-4 py-16 lg:grid-cols-2 lg:px-8 lg:py-20">
            <div>
              <h2 className="text-title font-semibold text-ink">Lo que el sello garantiza</h2>
              <ul className="mt-5 flex flex-col gap-3">
                {GUARANTEES.map((g) => (
                  <li key={g} className="flex gap-3 text-body text-ink"><Check aria-hidden className="mt-1 size-5 shrink-0 text-verified" strokeWidth={1.75} />{g}</li>
                ))}
              </ul>
            </div>
            <div>
              <h2 className="text-title font-semibold text-ink">Y lo que no promete</h2>
              <ul className="mt-5 flex flex-col gap-3">
                {LIMITS.map((g) => (
                  <li key={g} className="flex gap-3 text-body text-ink-muted"><Minus aria-hidden className="mt-1 size-5 shrink-0" strokeWidth={1.75} />{g}</li>
                ))}
              </ul>
            </div>
          </div>
        </section>

        <section id="roles" className="scroll-mt-16 mx-auto max-w-[1200px] px-4 py-16 lg:px-8 lg:py-24">
          <h2 className="text-display font-semibold text-ink">Cada quien ve lo suyo</h2>
          <p className="mt-2 mb-8 max-w-[60ch] text-body text-ink-muted">Cada persona entra con su propio usuario. Un taller nunca ve los datos de otro, y el mecánico no ve el dinero.</p>
          <div className="card overflow-hidden">
            <table className="w-full text-left">
              <caption className="sr-only">Qué puede hacer cada rol</caption>
              <thead className="hidden bg-surface-sunken text-caption text-ink-muted sm:table-header-group">
                <tr><th scope="col" className="px-5 py-3 font-medium">Rol</th><th scope="col" className="px-5 py-3 font-medium">Qué hace</th><th scope="col" className="px-5 py-3 font-medium">Qué ve</th></tr>
              </thead>
              <tbody className="divide-y divide-border">
                {ROLES.map((r) => (
                  <tr key={r.role} className="flex flex-col gap-1 px-4 py-4 sm:table-row sm:p-0">
                    <th scope="row" className="text-body font-medium text-ink sm:px-5 sm:py-4">{r.role}</th>
                    <td className="text-body-sm text-ink-muted sm:px-5 sm:py-4">{r.does}</td>
                    <td className="text-body-sm text-ink sm:px-5 sm:py-4 sm:whitespace-nowrap">{r.sees}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>

        <section className="border-t border-border bg-surface-sunken">
          <div className="mx-auto flex max-w-[1200px] flex-col items-start gap-6 px-4 py-16 lg:flex-row lg:items-center lg:justify-between lg:px-8">
            <div>
              <h2 className="text-title font-semibold text-ink">Recorra un taller de ejemplo</h2>
              <p className="mt-1 text-body text-ink-muted">Entre como dueño, recepción, mecánico o propietario de moto. Sin instalar nada y sin datos reales.</p>
            </div>
            <Link href="/login" className="inline-flex h-12 items-center gap-2 rounded-md bg-primary px-5 text-body-sm font-medium text-on-primary hover:bg-primary-hover">
              Elegir un rol <ArrowRight aria-hidden className="size-[18px]" strokeWidth={1.75} />
            </Link>
          </div>
        </section>
      </main>

      <footer className="border-t border-border">
        <div className="mx-auto flex max-w-[1200px] flex-col gap-2 px-4 py-8 text-caption text-ink-muted sm:flex-row sm:justify-between lg:px-8">
          <span>Nítida · Proyecto de Pruebas y Mantenimiento de Software</span>
          <span>Hecho para talleres de Pasto y de toda Colombia</span>
        </div>
      </footer>
    </div>
  );
}
