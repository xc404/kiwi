/** 分页记录集，对标 ExtJS 分页 Reader 结果。 */
export class Page<T> extends Array<T> {
  static readonly DefaultPageSize = 10;

  pageIndex: number;
  pageSize: number;
  totalCount: number;

  constructor(pgIx?: number, pgSize?: number, tot?: number, items?: T[]) {
    super();
    this.pageIndex = pgIx ?? 0;
    this.pageSize = pgSize ?? 0;
    this.totalCount = tot ?? 0;
    if (items && items.length > 0) {
      this.push(...items);
    }
  }
}
