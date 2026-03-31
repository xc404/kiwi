import { Component, effect, input, viewChild } from "@angular/core";
import { FormControl } from "@angular/forms";
import { CrudPage, PageConfig } from "@app/shared/components/crud/components/crud-page";
import { Editor } from "@app/shared/components/field/field-editor";

@Component({
  selector: 'system-dict-items',
  template: `
  <crud-page [pageConfig]="pageConfig" >
      </crud-page>
  `,
  styleUrls: ['./dict.component.less'],
  imports: [
    CrudPage,
  ]
})
export class DictItemsComponent {
  crudPage = viewChild(CrudPage);

  group = input<any>();

  constructor() {
    effect(() => {
      let group: any = this.group();
      let page = this.crudPage();

      if (group && page) {
        this.crudPage()?.load({ "groupCode": group.id });
        this.crudPage()!.defaultEditRecord["groupCode"] = group.id;
      }
    });
  }

  ngAfterViewInit(): void {
  }

  pageConfig = {
    title: "字典",
    crud: "system/dict",
    tableConfig: {
      showCheckbox: false,
      pageSize: 20,
    },
    fields: [
      {
        name: '字典编码',
        dataIndex: 'code',
        required: true,
      },
      {
        name: '字典名称',
        dataIndex: 'name',
        required: true,
      },
      {
        name: '字典Group',
        dataIndex: 'groupCode',
        column: "disabled",
        edit: {
          create: 'hidden',
          update: 'hidden',
        }
      },
      {
        name: '排序',
        dataIndex: 'dictSort',
        editor: Editor.Int,
        defaultValue: 10,
      },
      {
        name: '备注',
        width: 100,
        dataIndex: 'remark',
        editor: Editor.TextArea,
      }
    ]
  } as PageConfig;

}
