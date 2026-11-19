"use client";
import Link from "next/link";
import type { ReactNode } from "react";
import { Lockup } from "@/components/brand/logo";
import { ChainHero } from "@/components/illustrations";
import { ThemeToggle } from "./app-shell-theme";

/** Login y registro: formulario a la izquierda en móvil, panel de marca con la cadena en escritorio (1 : 1). */
export function AuthLayout({ title, subtitle, children }: { title: string; subtitle: string; children: ReactNode }) {
  return (
    <div className="grid grid-cols-1 min-h-screen lg:grid-cols-2">
      <div className="flex flex-col px-4 py-6 sm:px-8 lg:px-16">
        <header className="flex items-center justify-between">
          <Link href="/" aria-label="Nítida, portada"><Lockup size={32} animate /></Link>
          <ThemeToggle />
        </header>
        <main className="mx-auto flex w-full max-w-[420px] flex-1 flex-col justify-center py-10">
          <h1 className="text-title font-semibold text-ink">{title}</h1>
          <p className="mt-1 mb-8 text-body-sm text-ink-muted">{subtitle}</p>
          {children}
        </main>
      </div>
      <aside className="relative hidden flex-col justify-center overflow-hidden border-l border-border bg-surface-sunken px-16 lg:flex">
        <ChainHero className="w-full max-w-[520px]" />
        <div className="mt-10 max-w-[440px]">
          <p className="text-heading font-semibold text-ink">Cada servicio queda encadenado al anterior y firmado por el taller.</p>
          <p className="mt-2 text-body-sm text-ink-muted">
            Si alguien borra o cambia un registro después, la verificación lo detecta. El dueño de la moto lo comparte con un enlace cuando la vaya a vender.
          </p>
        </div>
      </aside>
    </div>
  );
}
