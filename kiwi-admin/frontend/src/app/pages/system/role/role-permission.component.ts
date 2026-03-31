import { Component, computed, effect, inject, input, model, OnInit, output, Signal, signal } from '@angular/core';
import { BaseHttpService } from '@app/core/services/http/base-http.service';
import { TreeUtils } from '@app/utils/treeUtils';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzCardModule } from "ng-zorro-antd/card";
import { NzGridModule } from 'ng-zorro-antd/grid';
import { NzTransferModule, TransferItem } from 'ng-zorro-antd/transfer';
import { NzTreeModule, NzTreeNodeOptions } from 'ng-zorro-antd/tree';
import { Permission, Role } from '../types';
import { NzTreeNodeKey } from 'ng-zorro-antd/core/tree';
import { get } from 'lodash';


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
    menuMap = signal<Map<string, any>>(new Map());
    expandedKeys: string[] = [];
    allPermissions = signal<Permission[]>([]);
    checkedMenus = signal<string[]>([]);
    checkedPermissions = signal<string[]>([]);

    permissionChange = output<Role>();

    constructor() { 
        effect(() => {
            this.checkedMenus.set(this.role()?.menuIds || []);

            let disabled = this.disabled();
        });
    }

    setCheckMenus(checkedKeys: NzTreeNodeKey[]): void {
        
        let newCheckedMenus = checkedKeys.map(key => key.toString());
        let oldCheckedMenus = this.checkedMenus();
        this.checkedMenus.set(newCheckedMenus);

        let deletedMenus = oldCheckedMenus.filter(id => !newCheckedMenus.includes(id));
        let addedMenus = newCheckedMenus.filter(id => !oldCheckedMenus.includes(id));

        let deletePermission = this.getMenusPemissions(deletedMenus);
        let addPermission = this.getMenusPemissions(addedMenus);

        let currentPermissions = new Set(this.checkedPermissions() || []);

        deletePermission.forEach(p => currentPermissions.delete(p));
        addPermission.forEach(p => currentPermissions.add(p));

        this.checkedPermissions.set(Array.from(currentPermissions));
    }

  

    permissionList = computed(() => {
        let checkedPermissions =  this.checkedPermissions();
        let isAdmin = this.isAdminRole();
        return this.allPermissions().map(perm => ({
            ...perm,
            title: perm.description || perm.key,
            // description: perm.description,
            direction: checkedPermissions.includes(perm.key) || isAdmin ? 'right' : 'left'
        } as TransferItem));
    });

    disabled = computed(() => {
        return !this.role() || this.role().code === 'admin';
    });

    ngOnInit(): void {
        this.loadAllMenus();
        this.loadAllPermissions();
    }

    loadAllMenus(): void {
        this.http.get(`/system/menu`).subscribe((res:any) => {
            this.allMenus.set(TreeUtils.convertToTreeNode(res.content));
            this.menuMap.set(TreeUtils.buildMap(res.content, 'id'));
        });
    }

    loadAllPermissions(): void {
        this.http.get(`/common/permission`).subscribe((res:any) => {
            this.allPermissions.set(res.content);
        });
    }

    menuTree = computed(() => {
        this.disabled();
        let menus = this.allMenus();
        let isAdmin = this.isAdminRole();
        let tree = TreeUtils.forEach(menus, node => {
            node.disableCheckbox = this.disabled();
            node.checked = isAdmin;
            return node;
        });
        return tree;
    });

    _checkedMenus = computed(() => {
        let isAdmin = this.isAdminRole();
        if (isAdmin) {
           return Array.from(this.menuMap().keys());
        }
        return this.checkedMenus();
    });
    
                

    isAdminRole = computed(() => {
        return this.role() && this.role().code === 'admin';
    });



    getMenusPemissions(menuIds: string[]): string[] {
        let permissions: string[] = [];
        menuIds.forEach(id => {
            let menu = this.menuMap().get(id);
            if (menu && menu.permissions) {
                permissions.push( ...menu.permissions.map((p: any) => p.permission));
            }
        });
        return permissions;
    };



    savePermissions(): void {
        let role = {
            ...this.role(),
            menuIds: this.checkedMenus(),
            permissions: this.checkedPermissions()
        }
        this.http.put(`/system/role/${this.role().id}`, role).subscribe(res => {
            
            this.permissionChange.emit(role);
        });
    }

    cancel(): void {

    }
}