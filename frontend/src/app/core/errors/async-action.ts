import { inject, signal } from "@angular/core";
import { FeedbackService } from "./feedback.service";

/** Estado da operação da página; os dados do domínio ficam nos seus serviços. */
export class AsyncAction {
  private readonly feedback = inject(FeedbackService);
  readonly busy = signal(false);

  async run(action: () => Promise<unknown>): Promise<boolean> {
    if (this.busy()) return false;
    this.feedback.clear();
    this.busy.set(true);
    try {
      await action();
      return true;
    } catch (error) {
      this.feedback.report(error);
      return false;
    } finally {
      this.busy.set(false);
    }
  }
}
