import { ErrorHandler, inject, Injectable } from "@angular/core";
import { FeedbackService } from "./feedback.service";

@Injectable()
export class AppErrorHandler implements ErrorHandler {
  private readonly feedback = inject(FeedbackService);
  handleError(error: unknown) {
    console.error(error);
    this.feedback.report(error);
  }
}
