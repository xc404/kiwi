import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { NzCardModule } from "ng-zorro-antd/card";
import { NzFormatEmitEvent, NzTreeModule, NzTreeNodeOptions } from "ng-zorro-antd/tree";
import { BaseHttpService } from '@app/core/services/http/base-http.service';
import { TreeUtils } from '@app/utils/treeUtils';
import { AppButton, AppButtonConfig } from "@app/shared/components/button/app.button";
import { NzGridModule } from 'ng-zorro-antd/grid';
import { NzModalModule } from "ng-zorro-antd/modal";
import { FormPanel } from "@app/shared/formly/panel/form-panel";
import { NzDividerComponent } from "ng-zorro-antd/divider";
import { NzMessageService } from 'ng-zorro-antd/message';
import { RolePermissionComponent } from "./role-permission.component";
import { Role } from '../types';

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

    roleNodes = signal<NzTreeNodeOptions[]>([] as any);
    selectedRole = signal<Role>(null as any);
    isEditing: boolean = false;


    addBtn: AppButtonConfig = {
        tooltip: "添加角色",
        nzSize: 'small',
        icon: 'plus',
        handler: () => this.showEdit()
    };
    editBtn: AppButtonConfig = {
        tooltip: "编辑角色",
        nzSize: 'small',
        icon: 'edit',
        handler: () => {
            if(!this.selectedRole()){
                this.messageService.warning("请先选择一个角色");
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
        },
    ]
    model = signal<any>({});
    constructor() { }

    ngOnInit(): void {
        this.loadRoles();
    }

    loadRoles(): void {
        // TODO: Implement API call to fetch roles
        this.httpService.get<any>('/system/role').subscribe(res => {
            let role = res.content;
            const nodes: NzTreeNodeOptions[] = TreeUtils.convertToTreeNode(role, 'id', 'name', 'children');
            this.roleNodes.set(nodes);
        });
    }

    createRole(): void {
        this.isEditing = true;
    }


    saveRole(): void {
        let model = this.model();
        if (model.id) {

            this.httpService.put(`/system/role/${model.id}`, model).subscribe(res => {
                this.completeEdit();
            });
        } else {
            this.httpService.post('/system/role', model).subscribe(res => {
                this.completeEdit();
            });

        }
    }

    completeEdit(): void {
       this.editModalVisible.set(false);
        this.loadRoles();
    }

    deleteRole(id: number): void {
        // TODO: Implement API call to delete role
        this.loadRoles();
    }

    cancelEdit(): void {
        this.isEditing = false;
        this.editModalVisible.set(false);
    }

    onRoleClick(event: NzFormatEmitEvent): void {
        this.selectedRole.set(event.node?.origin as unknown as Role);
    }

    showEdit(role?: Role): void {
        if(role){
            this.model.set({...role});
        }else{
            this.model.set({});
        }
        this.editModalVisible.set(true);
    }

    updateSelectPermission(role: Role): void {
        let selected  = this.selectedRole();
        selected.permissions = role.permissions;
        selected.menuIds = role.menuIds;
    }
}