import type { Metadata, Viewport } from "next";
import { Inter, JetBrains_Mono } from "next/font/google";
import "./globals.css";
import { Providers } from "./providers";

// next/font descarga las fuentes en el build y las sirve desde el mismo origen: no hay llamadas a Google en el navegador.
const inter = Inter({ variable: "--font-inter", subsets: ["latin"], display: "swap" });
const mono = JetBrains_Mono({ variable: "--font-jetbrains", subsets: ["latin"], display: "swap" });

export const metadata: Metadata = {
  title: { default: "Nítida · Historial verificable para talleres de motos", template: "%s · Nítida" },
  description:
    "Recepción, órdenes de trabajo, pagos y caja para talleres de motos, con un historial de cada moto firmado y verificable por un enlace público.",
  icons: { icon: "/brand/favicon.svg" },
};

export const viewport: Viewport = {
  themeColor: [
    { media: "(prefers-color-scheme: light)", color: "#F9F6F2" },
    { media: "(prefers-color-scheme: dark)", color: "#0F131E" },
  ],
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="es-CO" suppressHydrationWarning className={`${inter.variable} ${mono.variable} h-full`}>
      <body className="min-h-full">
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
