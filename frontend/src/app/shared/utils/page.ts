export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  total: number;
}
export const PAGE_SIZE = 10;
export const emptyPage = <T>(): Page<T> => ({
  items: [],
  page: 0,
  size: PAGE_SIZE,
  total: 0,
});
