import type { Role } from "../../../core/auth/user.model";
export interface Account {
  id: string;
  name: string;
  email: string;
  role: Role;
}
