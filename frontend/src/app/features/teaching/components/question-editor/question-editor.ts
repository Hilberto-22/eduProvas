import { Component, inject } from "@angular/core";
import { CommonModule } from "@angular/common";
import { FormsModule } from "@angular/forms";
import { AsyncAction } from "../../../../core/errors/async-action";
import { AssessmentsService } from "../../services/assessments.service";

@Component({
  selector: "app-question-editor",
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: "./question-editor.html",
})
export class QuestionEditor {
  readonly store = inject(AssessmentsService);
  readonly action = new AsyncAction();
  addQuestion() {
    return this.action.run(() => this.store.addQuestion());
  }
  addOption() {
    if (this.store.options.length < 5) this.store.options.push("");
  }
  removeOption(index: number) {
    if (this.store.options.length <= 2) return;
    this.store.options.splice(index, 1);
    if (this.store.correct === index) this.store.correct = 0;
    else if (this.store.correct > index) this.store.correct--;
  }
  trackIndex(index: number) {
    return index;
  }
}
