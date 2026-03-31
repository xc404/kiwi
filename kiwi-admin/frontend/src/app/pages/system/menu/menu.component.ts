import { Component, computed, inject, OnInit, signal, viewChild } from "@angular/core";
import { CrudPage, PageConfig } from "@app/shared/components/crud/components/crud-page";
import { PageHeaderComponent } from "@app/shared/components/page-header/page-header.component";
import { MenuSelectType } from "@app/shared/formly/types/menu-select.type";
import { ModalWrapService } from "@app/widget/base-modal";
import { NzButtonModule } from "ng-zorro-antd/button";
import { NzCardModule } from "ng-zorro-antd/card";
import { NzIconModule } from "ng-zorro-antd/icon";
import { NzModalService } from "ng-zorro-antd/modal";
import { MenuPermissionComponent } from "./menu-permission";
import { NzModalWrapService } from "@app/widget/modal/nz-modal-wrap.service";
import { ColumnToken } from "@app/shared/components/table/column";




@Component({
  selector: 'system-menu-component',
  templateUrl: './menu.component.html',
  imports: [
    PageHeaderComponent,
    CrudPage,
    NzCardModule,
    NzButtonModule,
    NzIconModule,
  ],
  standalone: true
})
export class MenuComponent implements OnInit {


  zorroIconTpl = viewChild.required("zorroIconTpl");
  modalService = inject(NzModalWrapService);
  crudPage = viewChild.required(CrudPage);

  pageConfig = computed(() => {
    return {
      title: "菜单",
      "initializeData": true,
      crud: "system/menu",
      tableConfig: {
        pageSize: 0,
        type: "tree"
      },
      columnActions: [
        {
          name: '权限管理',
          disabled: () => {
            let record = inject(ColumnToken, { optional: true })?.getRecord();
            return record.menuType == "M"
          },
          handler: () => {
            let record = inject(ColumnToken, { optional: true })?.getRecord();
            this.popupPermission(record);
          }
        },
        {
          name: '添加菜单',
          disabled: () => {
            let record = inject(ColumnToken, { optional: true })?.getRecord();
            return record.menuType == "C"
          },
          handler: () => {
            let record = inject(ColumnToken).getRecord();
            this.crudPage().popupAdd({ parentId: record.id });
          }
        }
      ],
      fields: [
        {
          name: '菜单名称',
          dataIndex: 'name',
          required: true,
          width: 300,
        },
        {
          name: '父菜单',
          dataIndex: 'parentId',
          column: false,
          required: true,
          editor: MenuSelectType,
          defaultValue: "0",
        },
        {
          name: '路由地址',
          dataIndex: 'path',
          required: true,
        },
        {
          name: '菜单类型',
          dataIndex: 'menuType',
          dictKey: 'system_menu_type',
          defaultValue: "C",
          editor: 'radio',
        },
        {
          name: '菜单图标',
          dataIndex: 'icon',
          editor: 'icon-select',
          tdTemplate: this.zorroIconTpl()
        },
        {
          name: '菜单排序',
          dataIndex: 'sort',
          type: 'number'
        },
        {
          name: '菜单状态',
          dataIndex: 'status',
          dictKey: 'menu_status',
          defaultValue: "0",
          editor: 'radio'
        },

      ]
    } as PageConfig
  });

  popupPermission(menu: any) {
    
    let modal = this.modalService.create({
      nzWidth: "75vw",
      nzTitle: '权限管理',
      nzContent: MenuPermissionComponent,
      nzData: menu,
      nzOnOk: () => {
        modal.componentInstance?.save();
      }    
    });
  }



  ngOnInit(): void {
  }
}