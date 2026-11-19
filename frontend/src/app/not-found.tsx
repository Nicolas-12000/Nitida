import Link from "next/link";
import { Lockup } from "@/components/brand/logo";
import { BrokenLinkFigure } from "@/components/illustrations";

export default function NotFound() {
  return (
    <main className="flex min-h-screen flex-col items-center justify-center px-4 text-center">
      <Lockup size={28} />
      <div className="mt-10"><BrokenLinkFigure /></div>
      <h1 className="mt-4 text-title font-semibold">No encontramos esta página</h1>
      <p className="mt-2 max-w-sm text-body-sm text-ink-muted">Puede que el enlace esté mal escrito o que la página ya no exista.</p>
      <Link href="/" className="mt-6 inline-flex h-12 items-center rounded-md bg-primary px-5 text-body-sm font-medium text-on-primary hover:bg-primary-hover lg:h-10">Ir a la portada</Link>
    </main>
  );
}
