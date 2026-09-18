import { Component, inject } from "@angular/core";
import { CommonModule } from "@angular/common";
import { FormsModule } from "@angular/forms";
import { AsyncAction } from "../../../../core/errors/async-action";
import { AttemptState } from "../../services/attempt-state.service";
import { AttemptSync } from "../../services/attempt-sync.service";

@Component({
  selector: "app-exam-runner",
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: "./exam-runner.html",
})
export class ExamRunner {
  readonly state = inject(AttemptState);
  readonly sync = inject(AttemptSync);
  readonly action = new AsyncAction();
  submit() {
    return this.action.run(() => this.sync.submit());
  }
  enterFullscreen() {
    return this.action.run(async () => {
      if (!document.fullscreenElement)
        await document.documentElement.requestFullscreen();
      this.state.fullscreen.set(!!document.fullscreenElement);
    });
  }
}
