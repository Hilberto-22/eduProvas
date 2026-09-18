import { Component, computed, input, output } from "@angular/core";
import type { Page } from "../../utils/page";

@Component({
  selector: "app-pagination",
  standalone: true,
  template: `@if (page().total > page().size) {
    <div class="pagination" aria-label="Paginação">
      <button
        type="button"
        [disabled]="busy() || page().page === 0"
        (click)="change.emit(-1)"
      >
        Anterior
      </button>
      <span aria-live="polite"
        >Página {{ page().page + 1 }} de {{ count() }} ·
        {{ page().total }} registros</span
      >
      <button
        type="button"
        [disabled]="busy() || (page().page + 1) * page().size >= page().total"
        (click)="change.emit(1)"
      >
        Próxima
      </button>
    </div>
  }`,
})
export class Pagination {
  readonly page = input.required<Page<unknown>>();
  readonly busy = input(false);
  readonly change = output<number>();
  readonly count = computed(() =>
    Math.max(1, Math.ceil(this.page().total / this.page().size)),
  );
}
