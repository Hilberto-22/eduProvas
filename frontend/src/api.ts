import { Injectable } from "@angular/core";

export interface User {
  id: string;
  name: string;
  role: "ADMIN" | "PROFESSOR" | "ALUNO";
}
@Injectable({ providedIn: "root" })
export class Api {
  onUnauthorized?: () => void;
  token = sessionStorage.getItem("token") || "";
  user: User | null = JSON.parse(sessionStorage.getItem("user") || "null");
  async call(path: string, method = "GET", body?: unknown): Promise<any> {
    const response = await fetch("/api" + path, {
      signal: AbortSignal.timeout(15000),
      method,
      headers: {
        "Content-Type": "application/json",
        ...(this.token ? { Authorization: "Bearer " + this.token } : {}),
      },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    if (!response.ok) {
      const error = await response.json().catch(() => ({}));
      if (response.status === 401 && path !== "/auth/login")
        this.onUnauthorized?.();
      throw new Error(
        error.message ||
          (response.status === 401
            ? "Sessão expirada ou credenciais inválidas. Entre novamente."
            : "Não foi possível concluir (" + response.status + ")."),
      );
    }
    const text = await response.text();
    return text ? JSON.parse(text) : undefined;
  }
  async login(email: string, password: string) {
    const result = await this.call("/auth/login", "POST", { email, password });
    this.token = result.token;
    this.user = result.user;
    sessionStorage.setItem("token", this.token);
    sessionStorage.setItem("user", JSON.stringify(this.user));
  }
  logout() {
    this.token = "";
    this.user = null;
    sessionStorage.removeItem("token");
    sessionStorage.removeItem("user");
  }
}
