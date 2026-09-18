import { inject } from "@angular/core";
import type { Routes } from "@angular/router";
import { AuthSession } from "./core/auth/auth-session.service";
import { authGuard, guestGuard, roleGuard } from "./core/guards/auth.guard";

export const APP_ROUTES: Routes = [
  {
    path: "",
    pathMatch: "full",
    redirectTo: () => {
      const session = inject(AuthSession);
      return session.authenticated() ? session.home() : "/login";
    },
  },
  {
    path: "login",
    canActivate: [guestGuard],
    loadChildren: () =>
      import("./features/auth/auth.routes").then((m) => m.AUTH_ROUTES),
  },
  {
    path: "student",
    canActivate: [authGuard, roleGuard("ALUNO")],
    loadChildren: () =>
      import("./features/student/student.routes").then((m) => m.STUDENT_ROUTES),
  },
  {
    path: "",
    canActivate: [authGuard, roleGuard("ADMIN", "PROFESSOR")],
    loadChildren: () =>
      import("./features/teaching/teaching.routes").then(
        (m) => m.TEACHING_ROUTES,
      ),
  },
  { path: "**", redirectTo: "" },
];
