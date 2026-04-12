import { Component, computed, DestroyRef, inject, OnInit, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { CrudPage, PageConfig } from '@app/shared/components/crud/components/crud-page';
import { Editor } from '@app/shared/components/field/field-editor';
import { PageHeaderComponent } from '@app/shared/components/page-header/page-header.component';
import { BaseHttpService } from '@app/core/services/http/base-http.service';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzCardModule } from 'ng-zorro-antd/card';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzSplitterModule } from 'ng-zorro-antd/splitter';
import { DictItemsComponent } from './dict-items.component';
import { actionColumn, ColumnToken } from '@app/shared/components/table/column';
import { columnAction } from '@app/shared/components/crud/actions';


@Component({
  selector: 'system-dict-component',
  templateUrl: './dict.component.html',
  styleUrls: ['./dict.component.less'],
  imports: [
    PageHeaderComponent,
    CrudPage,
    DictItemsComponent,
    NzCardModule,
    NzButtonModule,
    NzIconModule,
    NzSplitterModule
  ],
  standalone: true
})
export class DictComponent implements OnInit {

  crudPage = viewChild(CrudPage);

  dictItemsVisible = signal(false);

  private readonly route = inject(ActivatedRoute);
  private readonly http = inject(BaseHttpService);
  private readonly destroyRef = inject(DestroyRef);

  selectItem = signal<any>(undefined);

  mainSise = computed(() => {
    return this.dictItemsVisible() ? "50%" : "100%";
  });



  groupPageConfig: PageConfig = {
    title: "字典",
    "initializeData": true,
    crud: "system/dict/group",
    tableConfig: {
      showCheckbox: false,
      pageSize: 20,
    },
    columnActions: [
      columnAction({
        name: '子目录',
        icon: "setting",
        handler: () => {
          let item = inject(ColumnToken).getRecord();
          this.viewDict(item);
        }
      })
    ],
    fields: [
      {
        name: '分类编码',
        dataIndex: 'groupCode',
        search: true,
        required: true,
        edit: {
          create: "enabled",
          update: "readonly"
        }
      },
      {
        name: '分类名称',
        dataIndex: 'groupName',
        search: true,
        required: true,
      },
      {
        name: '状态',
        dataIndex: 'status',
        dictKey: 'system_status',
        defaultValue: "0",
        search: true,
      },
      {
        name: '备注',
        dataIndex: 'remark',
        editor: Editor.TextArea,
      }
    ]
  };

  viewDict(item: any) {
    if (item) {
      this.dictItemsVisible.set(true);
    }
    this.selectItem.set(item);
  }

  close() {
    // this.selectItem.set(null);
    this.dictItemsVisible.set(false);
  }

  ngOnInit(): void {
    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(params => {
      const groupCode = params.get('groupCode');
      if (groupCode) {
        this.openGroupByCodeFromRoute(groupCode);
      }
    });
  }

  /** AI 跳转或带 query 进入时，根据 groupCode 打开右侧字典项面板 */
  private openGroupByCodeFromRoute(groupCode: string): void {
    this.http
      .get<{ content?: Array<{ id?: string; groupCode?: string; groupName?: string }> }>('system/dict/group', {
        page: 0,
        size: 20,
        groupCode
      }, { showLoading: false })
      .subscribe({
        next: page => {
          const rows = page?.content ?? [];
          const row = rows.find(r => r.groupCode === groupCode || r.id === groupCode);
          if (row) {
            this.viewDict(row);
          }
        },
        error: () => {}
      });
  }

}




