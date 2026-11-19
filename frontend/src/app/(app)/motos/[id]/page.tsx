import { Suspense } from "react";
import { TimelineSkeleton } from "@/components/nitida/history";
import { MotoHistory } from "./moto-history";

// Depende de la sesión del navegador: no hay navegación instantánea que validar.
export const instant = false;

export default function MotoPage() {
  return (
    <Suspense fallback={<TimelineSkeleton />}>
      <MotoHistory />
    </Suspense>
  );
}
