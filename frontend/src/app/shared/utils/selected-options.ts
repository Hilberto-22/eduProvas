import { signal } from "@angular/core";

/** Guarda somente a página atual e opções selecionadas fora dela. */
export class SelectedOptions<T extends { id: string }> {
  private readonly cache = signal(new Map<string, T>());

  remember(items: T[], ids: string[]) {
    const selected = ids
      .map((id) => this.cache().get(id))
      .filter((item): item is T => !!item);
    this.cache.set(
      new Map([...selected, ...items].map((item) => [item.id, item])),
    );
  }

  set(item: T) {
    this.cache.update((cache) => new Map(cache).set(item.id, item));
  }
  get(id: string) {
    return this.cache().get(id) || null;
  }
  options(items: T[], ids: string[]) {
    const selected = [...new Set(ids)]
      .filter((id) => id && !items.some((item) => item.id === id))
      .map((id) => this.cache().get(id))
      .filter((item): item is T => !!item);
    return [...selected, ...items];
  }
}
