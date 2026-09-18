import type { CanDeactivateFn } from "@angular/router";

export const pendingExamGuard: CanDeactivateFn<{ canLeave: () => boolean }> = (
  page,
) => page.canLeave();
