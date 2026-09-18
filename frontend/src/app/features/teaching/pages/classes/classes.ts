import { Component, inject, OnInit } from "@angular/core";
import { CommonModule } from "@angular/common";
import { FormsModule } from "@angular/forms";
import { AsyncAction } from "../../../../core/errors/async-action";
import { FeedbackService } from "../../../../core/errors/feedback.service";
import { Pagination } from "../../../../shared/ui/pagination/pagination";
import { UserForm } from "../../../../shared/ui/user-form/user-form";
import type { UserFormValue } from "../../../../shared/ui/user-form/user-form.model";
import { ClassesService } from "../../services/classes.service";

@Component({
  selector: "app-classes",
  standalone: true,
  imports: [CommonModule, FormsModule, Pagination, UserForm],
  templateUrl: "./classes.html",
})
export class ClassesPage implements OnInit {
  readonly store = inject(ClassesService);
  readonly action = new AsyncAction();
  private readonly feedback = inject(FeedbackService);
  ngOnInit() {
    void this.action.run(() => this.store.classes.load());
  }
  createClass() {
    return this.action.run(() => this.store.create());
  }
  selectClass() {
    return this.action.run(() => this.store.selectClass());
  }
  enroll() {
    return this.action.run(() => this.store.enroll());
  }
  createStudent(value: UserFormValue) {
    return this.action.run(async () => {
      await this.store.createStudent(value);
      this.feedback.notice.set("Cadastro concluído.");
    });
  }
  changePage(key: "classes" | "students", delta: number) {
    return this.action.run(() => this.store[key].change(delta));
  }
}
