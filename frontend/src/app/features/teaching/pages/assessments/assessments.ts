import { Component, inject, OnInit } from "@angular/core";
import { CommonModule } from "@angular/common";
import { FormsModule } from "@angular/forms";
import { AsyncAction } from "../../../../core/errors/async-action";
import { Pagination } from "../../../../shared/ui/pagination/pagination";
import { AssessmentsService } from "../../services/assessments.service";
import { QuestionEditor } from "../../components/question-editor/question-editor";

@Component({
  selector: "app-assessments",
  standalone: true,
  imports: [CommonModule, FormsModule, Pagination, QuestionEditor],
  templateUrl: "./assessments.html",
})
export class AssessmentsPage implements OnInit {
  readonly store = inject(AssessmentsService);
  readonly action = new AsyncAction();
  ngOnInit() {
    void this.action.run(() => this.store.assessments.load());
  }
  createAssessment() {
    return this.action.run(() => this.store.create());
  }
  selectAssessment() {
    return this.action.run(() => this.store.selectAssessment());
  }
  changePage(key: "assessments", delta: number) {
    return this.action.run(() => this.store.assessments.change(delta));
  }
}
