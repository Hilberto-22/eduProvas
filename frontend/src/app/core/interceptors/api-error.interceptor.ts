import { inject } from "@angular/core";
import { HttpErrorResponse, HttpInterceptorFn } from "@angular/common/http";
import { Router } from "@angular/router";
import { catchError, throwError, timeout, TimeoutError } from "rxjs";
import { AuthSession } from "../auth/auth-session.service";
import { API_URL, HTTP_TIMEOUT_MS } from "../config/api.config";
import { FeedbackService } from "../errors/feedback.service";

export const apiErrorInterceptor: HttpInterceptorFn = (request, next) => {
  const session = inject(AuthSession);
  const router = inject(Router);
  const feedback = inject(FeedbackService);
  const apiUrl = inject(API_URL);
  if (!request.url.startsWith(apiUrl + "/")) return next(request);
  const token = session.token();
  return next(request).pipe(
    timeout(HTTP_TIMEOUT_MS),
    catchError((error: unknown) => {
      let message = "Falha de conexão";
      if (error instanceof TimeoutError)
        message = "Tempo de conexão esgotado. Tente novamente.";
      if (error instanceof HttpErrorResponse) {
        message =
          error.error?.message ||
          (error.status === 401
            ? "Sessão expirada ou credenciais inválidas. Entre novamente."
            : error.status === 0
              ? "Falha de conexão"
              : "Não foi possível concluir (" + error.status + ").");
        // A resposta de uma sessão anterior não encerra um login mais recente.
        if (
          error.status === 401 &&
          request.url !== apiUrl + "/auth/login" &&
          token &&
          token === session.token()
        ) {
          session.clear();
          message =
            "Sua sessão expirou. Entre novamente para retomar a prova. O prazo continua correndo.";
          feedback.error.set(message);
          void router.navigateByUrl("/login");
        }
      }
      return throwError(() => new Error(message));
    }),
  );
};
