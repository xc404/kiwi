import { CommonModule } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { BaseHttpService } from '@app/core/services/http/base-http.service';
import { AppButton, AppButtonConfig } from '@app/shared/components/button/app.button';
import { FormPanel } from '@app/shared/formly/panel/form-panel';
import { TreeUtils } from '@app/utils/treeUtils';

import { NzCardModule } from 'ng-zorro-antd/card';
import { NzDividerComponent } from 'ng-zorro-antd/divider';
import { NzGridModule } from 'ng-zorro-antd/grid';
import { NzMessageService } from 'ng-zorro-antd/message';
import { NzModalModule } from 'ng-zorro-antd/modal';
import { NzFormatEmitEvent, NzTreeModule, NzTreeNodeOptions } from 'ng-zorro-antd/tree';

import { RolePermissionComponent } from './role-permission.component';
import { Role } from '../types';

interface RoleFormModel {
  id?: string | number;
  [key: string]: unknown;
}

@Component({
  selector: 'app-role',
  standalone: true,
  imports: [CommonModule, FormsModule, NzCardModule, NzTreeModule, AppButton, NzGridModule, NzModalModule, FormPanel, NzDividerComponent, RolePermissionComponent],
  templateUrl: './role.component.html',
  styleUrls: ['role.component.css']
})
export class RoleComponent implements OnInit {
  httpService = inject(BaseHttpService);

  messageService = inject(NzMessageService);

  roleNodes = signal<NzTreeNodeOptions[]>([]);
  selectedRole = signal<Role | undefined>(undefined);
  isEditing: boolean = false;

  addBtn: AppButtonConfig = {
    tooltip: '添加角色',
    nzSize: 'small',
    icon: 'plus',
    handler: () => this.showEdit()
  };
  editBtn: AppButtonConfig = {
    tooltip: '编辑角色',
    nzSize: 'small',
    icon: 'edit',
    handler: () => {
      if (!this.selectedRole()) {
        this.messageService.warning('请先选择一个角色');
        return;
      }
      this.showEdit(this.selectedRole());
    }
  };
  editModalVisible = signal(false);
  fields = [
    {
      key: 'code',
      type: 'input',
      props: {
        label: '角色编码',
        required: true,
        placeholder: '请输入角色编码'
      }
    },
    {
      key: 'name',
      type: 'input',
      props: {
        label: '角色名称',
        required: true,
        placeholder: '请输入角色名称'
      }
    }
  ];
  model = signal<RoleFormModel>({});
  constructor() {}

  ngOnInit(): void {
    this.loadRoles();
  }

  loadRoles(): void {
    this.httpService.get<{ content: Role[] }>('/system/role').subscribe(res => {
      const role = res.content;
      const nodes: NzTreeNodeOptions[] = TreeUtils.convertToTreeNode(role, 'id', 'name', 'children');
      this.roleNodes.set(nodes);
    });
  }

  createRole(): void {
    this.isEditing = true;
  }

  saveRole(): void {
    const model = this.model();
    if (model.id) {
      this.httpService.put(`/system/role/${model.id}`, model).subscribe(() => {
        this.completeEdit();
      });
    } else {
      this.httpService.post('/system/role', model).subscribe(() => {
        this.completeEdit();
      });
    }
  }

  completeEdit(): void {
    this.editModalVisible.set(false);
    this.loadRoles();
  }

  deleteRole(id: number): void {
    this.httpService.delete(`/system/role/${id}`).subscribe(() => {
      this.loadRoles();
      const selected = this.selectedRole();
      if (selected && Number(selected.id) === id) {
        this.selectedRole.set(undefined);
      }
      this.messageService.success('删除成功');
    });
  }

  cancelEdit(): void {
    this.isEditing = false;
    this.editModalVisible.set(false);
  }

  onRoleClick(event: NzFormatEmitEvent): void {
    this.selectedRole.set(event.node?.origin as unknown as Role);
  }

  showEdit(role?: Role): void {
    if (role) {
      this.model.set({ ...role });
    } else {
      this.model.set({});
    }
    this.editModalVisible.set(true);
  }

  updateSelectPermission(role: Role): void {
    const selected = this.selectedRole();
    if (!selected) {
      return;
    }
    selected.permissions = role.permissions;
    selected.menuIds = role.menuIds;
  }
}
