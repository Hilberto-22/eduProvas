import { Component, inject } from "@angular/core";
import { NgIf } from "@angular/common";
import { FormsModule } from "@angular/forms";
import { Router } from "@angular/router";
import { AuthSession } from "../../../../core/auth/auth-session.service";
import { AsyncAction } from "../../../../core/errors/async-action";
import { FeedbackService } from "../../../../core/errors/feedback.service";
import { AuthService } from "../../services/auth.service";

@Component({
  selector: "app-login",
  standalone: true,
  imports: [NgIf, FormsModule],
  templateUrl: "./login.html",
})
export class LoginPage {
  private readonly auth = inject(AuthService);
  private readonly session = inject(AuthSession);
  private readonly router = inject(Router);
  readonly feedback = inject(FeedbackService);
  readonly action = new AsyncAction();
  email = "";
  password = "";

  async login() {
    if (
      await this.action.run(() => this.auth.login(this.email, this.password))
    ) {
      this.password = "";
      await this.router.navigateByUrl(this.session.home());
    }
  }
}
