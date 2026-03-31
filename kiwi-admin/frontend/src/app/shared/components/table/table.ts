import { AfterViewInit, Component, computed, effect, input, InputSignal, OnInit, output, signal } from '@angular/core';


import { NzSafeAny } from 'ng-zorro-antd/core/types';
import { NzResizeEvent } from 'ng-zorro-antd/resizable';
import { NzTableQueryParams, NzTableSize } from 'ng-zorro-antd/table';
import { ColumnConfig } from './column';
import { toObservable } from '@angular/core/rxjs-interop';

export type TableType = "tree" | "normal" | 'edit';


export interface AppTableConfig {
  needNoScroll?: boolean; //列表是否需要滚动条
  xScroll?: number; //列表横向滚动条
  yScroll?: number; //列表纵向滚动条
  virtualItemSize?: number; //虚拟滚动时每一列的高度，与 cdk itemSize 相同
  showCheckbox?: boolean; // 如果需要checkBox,则需要showCheckbox=true,并且使用app-ant-table组件时传入 [checkedCashArrayFromComment]="cashArray"，cashArray为业务组件中自己定义的数组，并且需要table中的data都有一个id属性
  pageSize?: number; // 每一页显示的数据条数（与页面中pageSize双向绑定）
  columns: ColumnConfig[]; // 列设置
  type?: TableType;
  enableTreeSelection?: false;
}

export abstract class TableComponentToken {
  tableSize!: NzTableSize;
  tableConfig!: InputSignal<AppTableConfig>;
  abstract selectedItems(): NzSafeAny[];
  abstract tableChangeDectction(): void;
}

export interface SortColumn {
  column: string;
  direction: undefined | 'desc' | 'asc';
}

export interface PageEvent {
  pageIndex: number;
  pageSize: number;
  sortColumn?: SortColumn;
}

@Component({
  template: '',
})
export class BaseTableComponent implements AfterViewInit {

  dataInited = false;

  readonly selectedItems = signal<NzSafeAny[]>([]);

  _allChecked = signal(false);

  _indeterminateChecked = signal(false);

  tableData = input<NzSafeAny[]>([]);

  pageSize = signal<number>(10);
  total = input<number>(0);
  loading = input<boolean>(false);

  _dataList = computed(() => {
    return this.tableData();
  });

  showPagination = computed(() => {
    return this.pageSize() > 0;
  })


  _tableSize = signal("default" as NzTableSize);
  set tableSize(value: NzTableSize) {
    this._tableSize.set(value);
  }

  get tableSize(): NzTableSize {
    return this._tableSize() as NzTableSize;
  }

  tableConfig = input.required<AppTableConfig>();

  _scrollConfig = computed(() => {
    return this.setScrollConfig(this.tableConfig());
  });

  readonly pageQueryChange = output<NzTableQueryParams>();
  readonly selectedChange = output<NzSafeAny[]>();

  readonly rowClick = output<NzSafeAny>();

  constructor() {
    toObservable(this._dataList).subscribe(items => {
      this.selectedItems.set([]);
      this.refreshCheckStatus();
    });
    effect(() => {
      if (this.tableConfig().pageSize != undefined) {
        this.pageSize.set(this.tableConfig().pageSize!);
      }
    });
  }



  setScrollConfig(value: AppTableConfig): { x: string; y: string } | {} {
    if (!value || value.needNoScroll) {
      return {};
    }
    const scrollConfig: { x?: string; y?: string } = { x: '100px' };
    if (value.xScroll !== undefined) {
      scrollConfig.x = `${value.xScroll}px`;
    }
    if (value.yScroll !== undefined) {
      scrollConfig.y = `${value.yScroll}px`;
    }
    return scrollConfig;
  }

  trackById(_: number, data: { id: number }): number {
    return data.id;
  }


  isItemSelected(item: NzSafeAny): boolean {
    return this.selectedItems().some(selectedItem => selectedItem.id === item.id);
  }


  public trackByTableHead(index: number, item: NzSafeAny): string {
    return `${item.title}-${index}`;
  }

  public trackByTableBody(index: number, item: NzSafeAny): string {
    return `${item.id}-${index}`;
  }



  onQueryParamsChange(tableQueryParams: NzTableQueryParams): void {
    tableQueryParams.sort = tableQueryParams.sort.filter(item => item.value && item.key);
    if (this.dataInited) {
      this.pageQueryChange.emit(tableQueryParams);
    }
    this.dataInited = true;
  }

  onRowClick(item: NzSafeAny): void {
    // this.rowClick.emit(item);
  }

  onResize({ width }: NzResizeEvent, col: string): void {
    this.tableConfig().columns = this.tableConfig().columns.map(e =>
      e.name === col
        ? {
          ...e,
          width: +`${width}`
        }
        : e
    ) as ColumnConfig[];
  }

  checkFn(dataItem: NzSafeAny, isChecked: boolean): void {
    this.selectedItems.update(items => {
      const index = items.findIndex(cashItem => cashItem.id === dataItem.id);
      if (isChecked) {
        if (index === -1) {
          items.push(dataItem);
        }
      } else {
        if (index !== -1) {
          items.splice(index, 1);
        }
      }
      return items;
    });
  }



  // 单选
  public checkRowSingle(isChecked: boolean, item: any): void {
    this.checkFn(item, isChecked);
    this.selectedChange.emit(this.selectedItems());
    this.refreshCheckStatus();
  }

  // 全选
  onAllChecked(isChecked: boolean): void {
    this._allChecked.set(isChecked);
    this._dataList().forEach(item => {
      this.checkFn(item, isChecked);
    });
    this.selectedChange.emit(this.selectedItems());
  }

  // 刷新复选框状态
  refreshCheckStatus(): void {
    let allChecked = this._dataList().length > 0 && this._dataList().every((item) => this.isItemSelected(item));
    let indeterminateChecked = this._dataList().length > 0 && this._dataList().some((item) => this.isItemSelected(item)) && !allChecked;
    this._allChecked.set(allChecked);
    this._indeterminateChecked.set(indeterminateChecked);
  }


  ngOnInit(): void {
  }
  ngAfterViewInit(): void {
  }
}