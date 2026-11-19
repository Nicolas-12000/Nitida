import { Suspense } from "react";
import { TimelineSkeleton } from "@/components/nitida/history";
import { OwnerMoto } from "./owner-moto";

// Depende de la sesión del navegador: no hay navegación instantánea que validar.
export const instant = false;

export default function OwnerMotoPage() {
  return (
    <Suspense fallback={<TimelineSkeleton />}>
      <OwnerMoto />
    </Suspense>
  );
}
