import { Component, computed, inject, signal, viewChild } from '@angular/core';
import { CrudPage, PageConfig } from '@app/shared/components/crud/components/crud-page';
import { Editor } from '@app/shared/components/field/field-editor';
import { PageHeaderComponent } from '@app/shared/components/page-header/page-header.component';
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
export class DictComponent {

  crudPage = viewChild(CrudPage);

  dictItemsVisible = signal(false);

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


}




