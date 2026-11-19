import type { Metadata } from "next";
import { AuthLayout } from "@/components/shell/auth-layout";
import { RecoverForm } from "./recover-form";

export const metadata: Metadata = { title: "Recuperar contraseña" };

export default function RecoverPage() {
  return (
    <AuthLayout title="Recuperar contraseña" subtitle="Le enviaremos un código al correo de su cuenta para elegir una contraseña nueva.">
      <RecoverForm />
    </AuthLayout>
  );
}
