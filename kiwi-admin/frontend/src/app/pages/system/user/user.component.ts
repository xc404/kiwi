import { Component, computed, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { PageHeaderComponent } from "@app/shared/components/page-header/page-header.component";
import { CrudPage, PageConfig } from "@app/shared/components/crud/components/crud-page";

interface User {
    id?: number;
    username: string;
    email: string;
    role: string;
    status: string;
}

@Component({
    selector: 'app-user',
    standalone: true,
    imports: [CommonModule, FormsModule, PageHeaderComponent, CrudPage],
    templateUrl: './user.component.html',
    styleUrls: ['user.component.css']
})
export class UserComponent {
   
    
  pageConfig = computed(() => {
    return {
      title: "用户",
      "initializeData": true,
      crud: "system/user",
      tableConfig: {
        pageSize: 0,
        type: "tree"
      },
      fields: [
        {
            name: '用户名',
            dataIndex: 'userName',
            required: true,
        },
        {
          name: '邮箱',
          dataIndex: 'email',
          required: true,
        },
        {
          name: '部门',
          dataIndex: 'deptId',
          dictKey: 'system_department',
        },
        {
          name: '角色',
          dataIndex: 'roleIds',
        },
        {
          name: '用户状态',
          dataIndex: 'status',
          dictKey: 'user_status',
          editor: 'radio',
        },
      ]
    } as PageConfig
  });
}