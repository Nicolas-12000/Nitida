import type { Metadata } from "next";
import { AuthLayout } from "@/components/shell/auth-layout";
import { RegisterForm } from "./register-form";

export const metadata: Metadata = { title: "Crear cuenta" };

export default function RegisterPage() {
  return (
    <AuthLayout title="Crear cuenta" subtitle="Para su taller o para seguir el historial de su moto.">
      <RegisterForm />
    </AuthLayout>
  );
}
