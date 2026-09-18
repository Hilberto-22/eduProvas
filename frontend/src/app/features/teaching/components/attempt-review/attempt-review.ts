import { Component, inject } from "@angular/core";
import { CommonModule } from "@angular/common";
import { FormsModule } from "@angular/forms";
import { AsyncAction } from "../../../../core/errors/async-action";
import { FeedbackService } from "../../../../core/errors/feedback.service";
import { ReviewService } from "../../services/review.service";

@Component({
  selector: "app-attempt-review",
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: "./attempt-review.html",
})
export class AttemptReview {
  readonly store = inject(ReviewService);
  readonly action = new AsyncAction();
  private readonly feedback = inject(FeedbackService);
  grade(id: string) {
    return this.action.run(async () => {
      await this.store.grade(id);
      this.feedback.notice.set("Correção salva.");
    });
  }
}
