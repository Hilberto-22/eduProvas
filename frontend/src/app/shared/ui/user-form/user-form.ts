import { Component, input, model, output } from "@angular/core";
import { NgIf } from "@angular/common";
import { FormsModule } from "@angular/forms";
import { emptyUserForm, UserFormValue } from "./user-form.model";

@Component({
  selector: "app-user-form",
  standalone: true,
  imports: [NgIf, FormsModule],
  templateUrl: "./user-form.html",
})
export class UserForm {
  readonly value = model<UserFormValue>(emptyUserForm());
  readonly admin = input(false);
  readonly busy = input(false);
  readonly submitted = output<UserFormValue>();
}
