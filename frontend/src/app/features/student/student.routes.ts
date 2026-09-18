import type { Routes } from "@angular/router";
import { pendingExamGuard } from "../../core/guards/pending-exam.guard";
export const STUDENT_ROUTES: Routes = [
  {
    path: "",
    canDeactivate: [pendingExamGuard],
    loadComponent: () =>
      import("./pages/student/student").then((m) => m.StudentPage),
  },
];
