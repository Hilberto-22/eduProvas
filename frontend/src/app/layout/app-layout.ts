import { Component, inject, input } from "@angular/core";
import { Router, RouterLink, RouterLinkActive } from "@angular/router";
import { AuthSession } from "../core/auth/auth-session.service";
import { FeedbackService } from "../core/errors/feedback.service";

@Component({
  selector: "app-layout",
  standalone: true,
  imports: [RouterLink, RouterLinkActive],
  templateUrl: "./app-layout.html",
})
export class AppLayout {
  readonly auth = inject(AuthSession);
  readonly feedback = inject(FeedbackService);
  private readonly router = inject(Router);
  readonly title = input.required<string>();
  readonly active = input(false);
  readonly timer = input("");
  readonly live = input(false);
  async logout() {
    if (this.active()) return;
    this.auth.clear();
    this.feedback.clear();
    await this.router.navigateByUrl("/login");
  }
}
