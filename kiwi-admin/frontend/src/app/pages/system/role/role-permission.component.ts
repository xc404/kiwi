import { Component, computed, effect, inject, input, OnInit, output, signal } from '@angular/core';

import { BaseHttpService } from '@app/core/services/http/base-http.service';
import { TreeUtils } from '@app/utils/treeUtils';

import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzCardModule } from 'ng-zorro-antd/card';
import { NzTreeNodeKey } from 'ng-zorro-antd/core/tree';
import { NzGridModule } from 'ng-zorro-antd/grid';
import { NzTransferModule, TransferItem } from 'ng-zorro-antd/transfer';
import { NzTreeModule, NzTreeNodeOptions } from 'ng-zorro-antd/tree';

import { Permission, Role } from '../types';

interface MenuPermission {
  permission: string;
}

interface MenuNode {
  permissions?: MenuPermission[];
}

@Component({
  selector: 'app-role-permission',
  templateUrl: './role-permission.component.html',
  styleUrls: ['role-permission.component.scss'],
  imports: [NzTreeModule, NzCardModule, NzGridModule, NzTransferModule, NzButtonModule],
  standalone: true
})
export class RolePermissionComponent implements OnInit {
  http = inject(BaseHttpService);
  role = input.required<Role>();
  loading = false;
  submitting = false;
  allMenus = signal<NzTreeNodeOptions[]>([]);
  menuMap = signal<Map<string, MenuNode>>(new Map());
  expandedKeys: string[] = [];
  allPermissions = signal<Permission[]>([]);
  checkedMenus = signal<string[]>([]);
  checkedPermissions = signal<string[]>([]);

  readonly permissionChange = output<Role>();

  constructor() {
    effect(() => {
      this.checkedMenus.set(this.role()?.menuIds || []);
      this.disabled();
    });
  }

  setCheckMenus(checkedKeys: NzTreeNodeKey[]): void {
    const newCheckedMenus = checkedKeys.map(key => key.toString());
    const oldCheckedMenus = this.checkedMenus();
    this.checkedMenus.set(newCheckedMenus);

    const deletedMenus = oldCheckedMenus.filter(id => !newCheckedMenus.includes(id));
    const addedMenus = newCheckedMenus.filter(id => !oldCheckedMenus.includes(id));

    const deletePermission = this.getMenusPemissions(deletedMenus);
    const addPermission = this.getMenusPemissions(addedMenus);

    const currentPermissions = new Set(this.checkedPermissions() || []);

    deletePermission.forEach(p => currentPermissions.delete(p));
    addPermission.forEach(p => currentPermissions.add(p));

    this.checkedPermissions.set(Array.from(currentPermissions));
  }

  permissionList = computed(() => {
    const checkedPermissions = this.checkedPermissions();
    const isAdmin = this.isAdminRole();
    return this.allPermissions().map(
      perm =>
        ({
          ...perm,
          title: perm.description || perm.key,
          // description: perm.description,
          direction: checkedPermissions.includes(perm.key) || isAdmin ? 'right' : 'left'
        }) as TransferItem
    );
  });

  disabled = computed(() => {
    return !this.role() || this.role().code === 'admin';
  });

  ngOnInit(): void {
    this.loadAllMenus();
    this.loadAllPermissions();
  }

  loadAllMenus(): void {
    this.http.get<{ content: MenuNode[] }>(`/system/menu`).subscribe(res => {
      this.allMenus.set(TreeUtils.convertToTreeNode(res.content));
      this.menuMap.set(TreeUtils.buildMap(res.content, 'id'));
    });
  }

  loadAllPermissions(): void {
    this.http.get<{ content: Permission[] }>(`/common/permission`).subscribe(res => {
      this.allPermissions.set(res.content);
    });
  }

  menuTree = computed(() => {
    this.disabled();
    const menus = this.allMenus();
    const isAdmin = this.isAdminRole();
    const tree = TreeUtils.forEach(menus, node => {
      node.disableCheckbox = this.disabled();
      node.checked = isAdmin;
      return node;
    });
    return tree;
  });

  _checkedMenus = computed(() => {
    const isAdmin = this.isAdminRole();
    if (isAdmin) {
      return Array.from(this.menuMap().keys());
    }
    return this.checkedMenus();
  });

  isAdminRole = computed(() => {
    return this.role() && this.role().code === 'admin';
  });

  getMenusPemissions(menuIds: string[]): string[] {
    const permissions: string[] = [];
    menuIds.forEach(id => {
      const menu = this.menuMap().get(id);
      if (menu && menu.permissions) {
        permissions.push(...menu.permissions.map(p => p.permission));
      }
    });
    return permissions;
  }

  savePermissions(): void {
    const role = {
      ...this.role(),
      menuIds: this.checkedMenus(),
      permissions: this.checkedPermissions()
    };
    this.http.put(`/system/role/${this.role().id}`, role).subscribe(() => {
      this.permissionChange.emit(role);
    });
  }

  cancel(): void {}
}
