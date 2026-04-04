import { ChangeDetectionStrategy, Component, computed, contentChild, inject, input, OnInit, output, signal } from '@angular/core';
import { FormGroup, FormsModule, ReactiveFormsModule } from '@angular/forms';


import { BaseHttpService } from '@app/core/services/http/base-http.service';
import { UserInfoStoreService } from '@app/core/services/store/common-store/userInfo-store.service';
import { ModalDragDirective } from '@app/widget/modal/modal-drag.directive';
import { FormlyConfig, FormlyFormBuilder } from '@ngx-formly/core';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzCardModule } from 'ng-zorro-antd/card';
import { NzWaveModule } from 'ng-zorro-antd/core/wave';
import { NzFormModule } from 'ng-zorro-antd/form';
import { NzGridModule } from 'ng-zorro-antd/grid';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzInputModule } from 'ng-zorro-antd/input';
import { NzMessageService } from 'ng-zorro-antd/message';
import { NzModalModule, NzModalService } from 'ng-zorro-antd/modal';
import { NzSelectModule } from 'ng-zorro-antd/select';
import { NzSwitchModule } from 'ng-zorro-antd/switch';
import { NzTableQueryParams } from 'ng-zorro-antd/table';
import { EMPTY, forkJoin, Observable, throwError } from 'rxjs';
import { catchError, take, tap } from 'rxjs/operators';
import { AppButtonConfig } from '../../button/app.button';
import { AppTableWrapComponent } from '../../table/app-table-wrap/app-table-wrap.component';
import { AppTableComponent } from '../../table/app-table/app-table.component';
import { AppTreeTableComponent } from '../../table/app-tree-table/app-tree-table.component';
import { actionColumn, ColumnActionConfig, ColumnConfig } from '../../table/column';
import { AppTableConfig, TableComponentToken, TableType } from '../../table/table';
import { AddAction, columnAction, DeleteAction, DeleteBatchAction, EditAction } from '../actions';
import { CrudDataSource } from '../crud-datastore';
import { crudConfig, CrudHttp, CrudHttpConfig } from '../crud-http';
import { CrudFieldConfig, getActionColumnWidth } from '../utils';
import { CrudEditForm, EditMode } from './crud-edit-form';
import { CrudSearchForm } from './crud-search-form';
import { AppEditTableComponent, RowChangeEvent } from '../../table/app-edit-table/app-edit-table.component';


export abstract class CrudPageToken {

  abstract popupEdit(record: any): void;

  abstract popupAdd(record?: any): void;

  abstract delete(record: any): void;

  abstract reloadTable(): void;

  abstract selectedItems(): any[];

  abstract deleteItems(items: any[]): void;
}



export interface PageConfig {
  title: string;
  crud: string | CrudHttpConfig | {
    get?: string | CrudHttpConfig | boolean;
    create?: string | CrudHttpConfig | boolean;
    update?: string | CrudHttpConfig | boolean;
    delete?: string | CrudHttpConfig | boolean;
    search?: string | CrudHttpConfig | boolean;
  };
  tableConfig?: {
    needNoScroll?: boolean; //列表是否需要滚动条
    xScroll?: number; //列表横向滚动条
    yScroll?: number; //列表纵向滚动条
    virtualItemSize?: number; //虚拟滚动时每一列的高度，与 cdk itemSize 相同
    showCheckbox?: boolean; // 如果需要checkBox,则需要showCheckbox=true,并且使用app-ant-table组件时传入 [checkedCashArrayFromComment]="cashArray"，cashArray为业务组件中自己定义的数组，并且需要table中的data都有一个id属性
    pageSize?: number;
    type?: TableType;
    enableTreeSelection?: boolean;
  };
  search?: {
    //搜索
    basicParams?: any;
  };
  editModal?: {
    width?: number | string;
    columns?: number;
    disabled?: boolean;
  };
  fields: CrudFieldConfig[]; // 列设置
  toolbarActions?: AppButtonConfig[];
  columnActions?: ColumnActionConfig[];
  initializeData?: boolean;
}



@Component({
  selector: 'crud-page',
  templateUrl: './crud-page.html',
  styleUrls: ['./crud-page.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [FormlyFormBuilder, FormlyConfig, { provide: CrudPageToken, useExisting: CrudPage }],
  imports: [
    NzGridModule,
    NzCardModule,
    FormsModule,
    NzFormModule,
    NzInputModule,
    NzSelectModule,
    NzButtonModule,
    NzWaveModule,
    NzIconModule,
    NzSwitchModule,
    ReactiveFormsModule,
    NzModalModule,
    ModalDragDirective,
    AppTableWrapComponent,
    AppTableComponent,
    AppEditTableComponent,
    AppTreeTableComponent,
    CrudSearchForm,
    CrudEditForm,
  ]
})
export class CrudPage implements OnInit, CrudPageToken {


  httpService = inject(BaseHttpService);
  userInfoService = inject(UserInfoStoreService);
  builder = inject(FormlyFormBuilder);
  formlyConfig = inject(FormlyConfig);
  modalService = inject(NzModalService);
  messageService = inject(NzMessageService);

  readonly tableComponent = contentChild(TableComponentToken);
  editModalVisible = signal(false);
  editTitle = signal("")
  editRecord = signal({} as any);
  defaultEditRecord: any = {};
  editMode = signal<EditMode>('create');
  editForm = new FormGroup({});

  pageConfig = input.required<PageConfig>();

  tableRowClick = output<any>();
  pageData = computed(() => {
    return this.crudDatasource().items();
  });


  searchFormFields = computed(() => {
    return this.pageConfig().fields.filter(field => field.search);
  });

  searchModel = computed(() => this.pageConfig().search?.basicParams ?? {});

  editFormFields = computed(() => {
    return this.pageConfig().fields.filter(field => field.edit != false && !!field.dataIndex);
  });

  editModalOptions = computed(() => {
    return {
      width: "75vw",
      columns: 2,
      ...this.pageConfig().editModal
    };
  });

  tableConfig = computed(() => {
    const tableConfig: AppTableConfig = {
      pageSize: 10,
      ...this.pageConfig().tableConfig,
      columns: this.columns()
    } as AppTableConfig;
    return tableConfig;
  });

  _crudConfig = computed(() => {
    return crudConfig(this.pageConfig().crud);
  });

  crudDatasource = computed(() => {
    let http = this.crudHttp();
    let sort = this.pageConfig().fields.filter(field => field.showSort && field.sortDirection).map(field => {
      return field.dataIndex + ' ' + field.sortDirection;
    }).join(',');
    return new CrudDataSource(http, {
      pageSize: this.pageConfig().tableConfig?.pageSize,
      sortParams: sort,
      basicParams: this.pageConfig().search?.basicParams
    });
  });

  enableEdit = computed(() => {
    if (this.pageConfig().editModal?.disabled === true) {
      return false;
    }
    let op = this._crudConfig().update;
    return op && this.userInfoService.hasPermission(op.permission);
  });

  enableDelete = computed(() => {
    let op = this._crudConfig().delete;
    return op && this.userInfoService.hasPermission(op.permission);
  });

  enableCreate = computed(() => {
    if (this.pageConfig().editModal?.disabled === true) {
      return false;
    }
    let op = this._crudConfig().create;
    return op && this.userInfoService.hasPermission(op.permission);
  });


  crudHttp = computed(() => {
    return new CrudHttp(this.httpService, this._crudConfig());
  });

  toolbarActions = computed(() => {
    if (this.pageConfig().toolbarActions) {
      return this.pageConfig().toolbarActions;
    }
    const actions: AppButtonConfig[] = [];
    if (this.enableCreate()) {
      actions.push(AddAction);
    }
    if (this.enableDelete() && this.tableConfig().showCheckbox) {
      actions.push(DeleteBatchAction);
    }
    return actions;
  });


  columns = computed((): ColumnConfig[] => {
    const columns: ColumnConfig[] = [];
    this.pageConfig().fields.filter(field => field.column != 'disabled' && field.column != false).forEach(field => {
      columns.push(this.toColumnField(field));
    });
    const columnActions: AppButtonConfig[] = [];
    if (this.enableEdit()) {
      columnActions.push(EditAction);
    }

    if (this.pageConfig().columnActions) {
      const extraColumnActions = this.pageConfig().columnActions?.map(action => columnAction(action)) || [];
      columnActions.push(...extraColumnActions);
    }
    if (this.enableDelete()) {
      columnActions.push(DeleteAction);
    }
    let width = getActionColumnWidth(columnActions);
    if (columnActions.length) {
      columns.push(actionColumn({
        name: "操作",
        width: width,
        fixed: true, // 是否固定单元格 （只有从最左边或最右边连续固定才有效）
        fixedDir: 'right', // 固定在左边还是右边，需要配合fixed来使用
        actions: columnActions
      }));
    }
    return columns;
  });

  tableLoading = computed(() => {
    return this.crudDatasource().loading();
  });
  pageQuery(params: NzTableQueryParams) {
    this.crudDatasource().search(params);
  }

  reloadTable() {
    this.crudDatasource().reload();
  }

  onRowClick(item: any) {
    this.tableRowClick.emit(item);
  }


  load(params: Record<string, unknown>) {
    const ds = this.crudDatasource();
    ds.basicParams = params;
    ds.reload();
  }

  doSearch(params: Record<string, unknown>) {
    this.crudDatasource().search(params);
  }

  selectedItems() {
    return this.tableComponent()?.selectedItems() ?? [];
  }

  toColumnField(field: CrudFieldConfig): ColumnConfig {
    return actionColumn(field);
  }


  popupAdd(record?: any) {
    this._popupEditModal('create', record);
  }


  popupEdit(record: any) {
    this._popupEditModal('update', record);
  }

  private _popupEditModal(mode: EditMode, record?: any) {
    this.editModalVisible.set(true);
    record = record ?? { ...this.defaultEditRecord };
    this.editRecord.set(record);
    this.editMode.set(mode);
    let title = mode == 'create' ? '新增' : '修改';
    this.editTitle.set(title + this.pageConfig().title);

  }

  delete(record: any) {
    this.deleteItem(record);
  }


  /**
   * 必须在内部 subscribe：模板 `(nzOnOk)="saveItem()"` 经 EventEmitter 调用时，
   * 返回值 Observable 不会被 ng-zorro 订阅，否则请求永不发出（表现为提交无反应/卡住）。
   */
  saveItem(): false | void {
    const mode = this.editMode();
    const item: any = this.editRecord();
    if (!this.editForm.valid) {
      this.messageService.warning('请检查表单填写是否完整');
      return false;
    }
    const formValue = { ...item, ...this.editForm.value };
    const save =
      mode === 'create' ? this.crudHttp().create(formValue) : this.crudHttp().update(formValue, item.id);
    const message = mode === 'create' ? '新增成功' : '修改成功';
    const failMsg = mode === 'create' ? '新增失败' : '修改失败';
    save
      .pipe(
        take(1),
        tap(() => {
          this.messageService.success(message);
          this.dismissEditModal();
          this.reloadTable();
        }),
        catchError(() => {
          this.messageService.error(failMsg);
          return EMPTY;
        })
      )
      .subscribe();
  }

  rowValueChange(event: RowChangeEvent) {
    this.crudHttp()
      .update(event.row, event.row.id)
      .pipe(
        take(1),
        tap(() => {
          this.messageService.success('修改成功');
          this.reloadTable();
        }),
        catchError(() => {
          this.messageService.error('修改失败');
          return EMPTY;
        })
      )
      .subscribe();
  }

  dismissEditModal() {
    this.editForm.reset();
    this.editModalVisible.set(false);
  }



  deleteItems(items: any[]) {
    if (!items?.length) {
      this.messageService.error('请选择要删除的记录');
      return;
    }

    this.modalService.confirm({
      nzTitle: `删除${this.pageConfig().title}`,
      nzContent: '确定要删除所选记录吗？',
      nzOkText: '确定',
      nzCancelText: '取消',
      nzOnOk: () =>{
        forkJoin(items.map(item => this.crudHttp().delete(item.id))).pipe(
          take(1),
          tap(() => {
            this.messageService.success('删除成功');
            this.reloadTable();
          }),
          catchError(err => {
            this.messageService.error('删除失败');
            return throwError(() => err);
          })
        ).subscribe();
      }
    });
  }

  deleteItem(item: any) {
    return this.deleteItems([item]);
  }

  ngOnInit(): void {
    if (this.pageConfig().initializeData) {
      this.reloadTable();
    }
  }
}


