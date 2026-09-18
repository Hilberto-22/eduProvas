import { Injectable, signal } from "@angular/core";

@Injectable({ providedIn: "root" })
export class FeedbackService {
  readonly error = signal("");
  readonly notice = signal("");

  clear() {
    this.error.set("");
    this.notice.set("");
  }
  report(error: unknown) {
    this.error.set(error instanceof Error ? error.message : "Falha de conexão");
  }
}
