// Cuentas del taller de ejemplo para el modo demostración (separadas de la semilla para no cargarla en el login).
export const DEMO_ACCOUNTS = [
  { id: "u-owner", role: "Dueño del taller", email: "carolina.benavides@ejido.test", name: "Carolina Benavides", hint: "Tablero, caja, alertas, equipo y ajustes" },
  { id: "u-desk", role: "Recepción", email: "andres.erazo@ejido.test", name: "Andrés Erazo", hint: "Recibe motos, cobra y entrega" },
  { id: "u-mech", role: "Mecánico", email: "jhon.pantoja@ejido.test", name: "Jhon Pantoja", hint: "Trabaja las órdenes y registra repuestos, sin ver dinero" },
  { id: "u-rider", role: "Propietaria de moto", email: "diana.munoz@correo.test", name: "Diana Muñoz", hint: "Ve el historial de su moto y lo comparte" },
  { id: "u-admin", role: "Administración de Nítida", email: "soporte@nitida.test", name: "Soporte Nítida", hint: "Verifica o suspende talleres" },
] as const;
