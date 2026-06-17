import { CommonModule } from '@angular/common';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { BaseHttpService } from '@app/core/services/http/base-http.service';
import { CrudPage, PageConfig } from '@app/shared/components/crud/components/crud-page';
import { PageHeaderComponent } from '@app/shared/components/page-header/page-header.component';
import { TreeUtils } from '@app/utils/treeUtils';

@Component({
  selector: 'app-user',
  standalone: true,
  imports: [CommonModule, FormsModule, PageHeaderComponent, CrudPage],
  templateUrl: './user.component.html',
  styleUrls: ['user.component.css']
})
export class UserComponent implements OnInit {
  private http = inject(BaseHttpService);

  deptNameMap = signal<Map<string, { name: string }>>(new Map());

  ngOnInit(): void {
    this.http.get<{ content: Array<{ id: string; name: string; children?: unknown[] }> }>('common/tree/sys-dept/0', { loadAll: true }).subscribe(res => {
      const map = TreeUtils.buildMap(res.content ?? [], 'id');
      this.deptNameMap.set(map);
    });
  }

  pageConfig = computed(() => {
    const deptMap = this.deptNameMap();
    return {
      title: '用户',
      initializeData: true,
      crud: 'system/user',
      tableConfig: {
        pageSize: 0,
        type: 'tree'
      },
      fields: [
        {
          name: '用户名',
          dataIndex: 'userName',
          required: true
        },
        {
          name: '邮箱',
          dataIndex: 'email',
          required: true
        },
        {
          name: '部门',
          dataIndex: 'deptId',
          editor: 'biz-tree-select',
          format: (deptId: string) => (deptId === '0' ? '总部门' : (deptMap.get(deptId)?.name ?? deptId ?? '-')),
          props: {
            groupCode: 'sys-dept',
            placeholder: '请选择部门',
            rootNode: { id: '0', name: '总部门' }
          }
        },
        {
          name: '角色',
          dataIndex: 'roleIds'
        },
        {
          name: '用户状态',
          dataIndex: 'status',
          dictKey: 'user_status',
          editor: 'radio'
        }
      ]
    } as PageConfig;
  });
}
