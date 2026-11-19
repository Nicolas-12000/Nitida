import type { Metadata } from "next";
import { Suspense } from "react";
import { PublicHistory } from "./public-history";

export const metadata: Metadata = {
  title: "Historial verificado de una moto",
  robots: { index: false, follow: false },
  referrer: "no-referrer",
};

export default function PublicHistoryPage() {
  return (
    <Suspense>
      <PublicHistory />
    </Suspense>
  );
}
