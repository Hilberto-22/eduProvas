import type { Routes } from "@angular/router";
import { roleGuard } from "../../core/guards/auth.guard";

export const TEACHING_ROUTES: Routes = [
  {
    path: "",
    loadComponent: () =>
      import("./pages/teaching-shell/teaching-shell").then(
        (m) => m.TeachingShell,
      ),
    canActivateChild: [roleGuard("ADMIN", "PROFESSOR")],
    children: [
      {
        path: "overview",
        data: { heading: "Visão geral" },
        loadComponent: () =>
          import("./pages/overview/overview").then((m) => m.OverviewPage),
      },
      {
        path: "classes",
        data: { heading: "Turmas e alunos" },
        loadComponent: () =>
          import("./pages/classes/classes").then((m) => m.ClassesPage),
      },
      {
        path: "assessments",
        data: { heading: "Avaliações" },
        loadComponent: () =>
          import("./pages/assessments/assessments").then(
            (m) => m.AssessmentsPage,
          ),
      },
      {
        path: "sessions",
        data: { heading: "Aplicações" },
        loadComponent: () =>
          import("./pages/sessions/sessions").then((m) => m.SessionsPage),
      },
      {
        path: "monitor",
        data: { heading: "Acompanhamento" },
        loadComponent: () =>
          import("./pages/monitor/monitor").then((m) => m.MonitorPage),
      },
      {
        path: "users",
        data: { heading: "Usuários" },
        canActivate: [roleGuard("ADMIN")],
        loadChildren: () =>
          import("../users/users.routes").then((m) => m.USERS_ROUTES),
      },
    ],
  },
];
