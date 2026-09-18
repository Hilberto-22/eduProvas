import { computed, Injectable, signal } from "@angular/core";
import type { LoginResponse, User } from "./user.model";

@Injectable({ providedIn: "root" })
export class AuthSession {
  private readonly identity = signal<User | null>(this.restoreUser());
  private readonly credential = signal(sessionStorage.getItem("token") || "");
  readonly user = this.identity.asReadonly();
  readonly token = this.credential.asReadonly();
  readonly authenticated = computed(() => !!this.user() && !!this.token());
  readonly home = computed(() =>
    this.user()?.role === "ALUNO" ? "/student" : "/overview",
  );

  start(result: LoginResponse) {
    sessionStorage.setItem("token", result.token);
    sessionStorage.setItem("user", JSON.stringify(result.user));
    this.credential.set(result.token);
    this.identity.set(result.user);
  }

  clear() {
    this.credential.set("");
    this.identity.set(null);
    sessionStorage.removeItem("token");
    sessionStorage.removeItem("user");
  }

  private restoreUser(): User | null {
    try {
      const user: unknown = JSON.parse(
        sessionStorage.getItem("user") || "null",
      );
      if (typeof user !== "object" || user === null) return null;
      const identity = user as Partial<User>;
      return typeof identity.id === "string" &&
        typeof identity.name === "string" &&
        ["ADMIN", "PROFESSOR", "ALUNO"].includes(identity.role || "")
        ? (identity as User)
        : null;
    } catch {
      return null;
    }
  }
}
