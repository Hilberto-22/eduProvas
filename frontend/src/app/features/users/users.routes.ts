import type { Routes } from "@angular/router";
export const USERS_ROUTES: Routes = [
  {
    path: "",
    loadComponent: () => import("./pages/users/users").then((m) => m.UsersPage),
  },
];
