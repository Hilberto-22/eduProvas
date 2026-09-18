import { computed, signal } from "@angular/core";
import { firstValueFrom, Observable } from "rxjs";
import { emptyPage, Page, PAGE_SIZE } from "./page";

/** Paginação com proteção contra respostas fora de ordem e contexto alterado. */
export class PagedList<T> {
  readonly data = signal<Page<T>>(emptyPage());
  readonly items = computed(() => this.data().items);
  private request = 0;

  constructor(
    private readonly fetchPage: (
      page: number,
      size: number,
    ) => Observable<Page<T>>,
    private readonly context: () => unknown = () => undefined,
    private readonly onLoaded: (result: Page<T>) => void = () => {},
  ) {}

  async load(page = this.data().page) {
    const request = ++this.request;
    const context = this.context();
    const result = await firstValueFrom(this.fetchPage(page, PAGE_SIZE));
    if (request !== this.request || context !== this.context()) return;
    this.data.set(result);
    this.onLoaded(result);
  }

  async change(delta: number) {
    const current = this.data();
    const next = current.page + delta;
    if (next < 0 || next * current.size >= current.total) return;
    await this.load(next);
  }

  reset() {
    this.request++;
    this.data.set(emptyPage());
  }
}
