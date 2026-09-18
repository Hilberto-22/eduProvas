import { inject } from "@angular/core";
import { CanActivateFn, Router } from "@angular/router";
import { AuthSession } from "../auth/auth-session.service";
import type { Role } from "../auth/user.model";

export const authGuard: CanActivateFn = () =>
  inject(AuthSession).authenticated() || inject(Router).parseUrl("/login");

export const guestGuard: CanActivateFn = () => {
  const session = inject(AuthSession);
  return !session.authenticated() || inject(Router).parseUrl(session.home());
};

export const roleGuard =
  (...roles: Role[]): CanActivateFn =>
  () => {
    const session = inject(AuthSession);
    const router = inject(Router);
    if (!session.authenticated()) return router.parseUrl("/login");
    return (
      roles.includes(session.user()!.role) || router.parseUrl(session.home())
    );
  };
