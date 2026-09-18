import { Component, inject, OnInit } from "@angular/core";
import { CommonModule } from "@angular/common";
import { AsyncAction } from "../../../../core/errors/async-action";
import { FeedbackService } from "../../../../core/errors/feedback.service";
import { Pagination } from "../../../../shared/ui/pagination/pagination";
import { UserForm } from "../../../../shared/ui/user-form/user-form";
import type { UserFormValue } from "../../../../shared/ui/user-form/user-form.model";
import { UsersService } from "../../services/users.service";

@Component({
  selector: "app-users",
  standalone: true,
  imports: [CommonModule, Pagination, UserForm],
  providers: [UsersService],
  templateUrl: "./users.html",
})
export class UsersPage implements OnInit {
  readonly store = inject(UsersService);
  readonly action = new AsyncAction();
  private readonly feedback = inject(FeedbackService);
  ngOnInit() {
    void this.action.run(() => this.store.users.load());
  }
  createUser(value: UserFormValue) {
    return this.action.run(async () => {
      await this.store.create(value);
      this.feedback.notice.set("Cadastro concluído.");
    });
  }
  changePage(key: "users", delta: number) {
    return this.action.run(() => this.store.users.change(delta));
  }
}
