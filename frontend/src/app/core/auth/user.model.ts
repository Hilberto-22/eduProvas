export type Role = "ADMIN" | "PROFESSOR" | "ALUNO";
export interface User {
  id: string;
  name: string;
  role: Role;
}
export interface LoginResponse {
  token: string;
  user: User;
}
