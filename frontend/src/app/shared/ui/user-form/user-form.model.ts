import type { Role } from "../../../core/auth/user.model";

export interface UserFormValue {
  name: string;
  email: string;
  password: string;
  role: Role;
}
export const emptyUserForm = (): UserFormValue => ({
  name: "",
  email: "",
  password: "",
  role: "ALUNO",
});
