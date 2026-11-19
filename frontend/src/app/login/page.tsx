import type { Metadata } from "next";
import { AuthLayout } from "@/components/shell/auth-layout";
import { LoginForm } from "./login-form";

export const metadata: Metadata = { title: "Ingresar" };

export default function LoginPage() {
  return (
    <AuthLayout title="Ingresar a Nítida" subtitle="Con el correo con el que lo invitaron a su taller o con el que registró su moto.">
      <LoginForm />
    </AuthLayout>
  );
}
