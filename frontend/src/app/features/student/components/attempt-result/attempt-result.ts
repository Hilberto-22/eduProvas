import { Component, input, output } from "@angular/core";
import { CommonModule } from "@angular/common";
import type { Attempt } from "../../models/attempt.model";

@Component({
  selector: "app-attempt-result",
  standalone: true,
  imports: [CommonModule],
  templateUrl: "./attempt-result.html",
})
export class AttemptResult {
  readonly attempt = input.required<Attempt>();
  readonly refresh = output<void>();
  readonly back = output<void>();
}
