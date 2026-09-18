import { inject, Injectable } from "@angular/core";
import { HttpClient } from "@angular/common/http";
import { firstValueFrom } from "rxjs";
import { AuthSession } from "../../../core/auth/auth-session.service";
import type { LoginResponse } from "../../../core/auth/user.model";
import { API_URL } from "../../../core/config/api.config";

@Injectable({ providedIn: "root" })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly session = inject(AuthSession);
  private readonly apiUrl = inject(API_URL);

  async login(email: string, password: string) {
    const result = await firstValueFrom(
      this.http.post<LoginResponse>(this.apiUrl + "/auth/login", {
        email,
        password,
      }),
    );
    this.session.start(result);
  }
}
