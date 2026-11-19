import { Suspense } from "react";
import { OrderDetail, OrderSkeleton } from "./order-detail";

// Depende de la sesión del navegador: no hay navegación instantánea que validar.
export const instant = false;

export default function OrderPage() {
  return (
    <Suspense fallback={<OrderSkeleton />}>
      <OrderDetail />
    </Suspense>
  );
}
