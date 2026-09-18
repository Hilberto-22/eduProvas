import { inject } from "@angular/core";
import { HttpInterceptorFn } from "@angular/common/http";
import { AuthSession } from "../auth/auth-session.service";
import { API_URL } from "../config/api.config";

export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const token = inject(AuthSession).token();
  const apiUrl = inject(API_URL);
  if (
    token &&
    request.url.startsWith(apiUrl + "/") &&
    request.url !== apiUrl + "/auth/login"
  ) {
    request = request.clone({
      setHeaders: { Authorization: "Bearer " + token },
    });
  }
  return next(request);
};
