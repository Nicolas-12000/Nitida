"use client";
import { Suspense, type ReactNode } from "react";
import { SessionProvider } from "@/lib/session";
import { AppShell } from "@/components/shell/app-shell";
import { Skeleton } from "@/components/ui/primitives";

function ShellSkeleton() {
  return (
    <div aria-busy="true" className="min-h-screen lg:grid lg:grid-cols-[240px_1fr]">
      <span className="sr-only">Cargando su taller…</span>
      <aside className="hidden h-screen flex-col gap-3 border-r border-border bg-surface p-5 lg:flex">
        <Skeleton className="h-7 w-28" />
        <Skeleton className="mt-4 h-12" />
        {Array.from({ length: 5 }, (_, i) => <Skeleton key={i} className="h-8" />)}
      </aside>
      <div className="mx-auto w-full max-w-[1200px] px-4 pt-8 lg:px-8">
        <Skeleton className="h-8 w-48" />
        <div className="mt-6 grid grid-cols-2 gap-3 lg:grid-cols-4">
          {Array.from({ length: 4 }, (_, i) => <Skeleton key={i} className="h-24 rounded-lg!" />)}
        </div>
      </div>
    </div>
  );
}

export function AppFrame({ children }: { children: ReactNode }) {
  // La sesión y la ruta solo existen en el navegador: el armazón se pre-renderiza como skeleton.
  return (
    <Suspense fallback={<ShellSkeleton />}>
      <SessionProvider fallback={<ShellSkeleton />}>
        <AppShell>{children}</AppShell>
      </SessionProvider>
    </Suspense>
  );
}
