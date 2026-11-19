import type { ReactNode } from "react";
import { AppFrame } from "@/components/shell/app-frame";

// El área del taller depende de la sesión del navegador (cookie SESSION, leída con /auth/me desde el cliente):
// no hay contenido que pre-renderizar ni navegación instantánea que validar aquí.
export const instant = false;

export default function AppLayout({ children }: { children: ReactNode }) {
  return <AppFrame>{children}</AppFrame>;
}
