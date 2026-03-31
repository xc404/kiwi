import { Component, computed, effect, inject, input, OnInit, signal, viewChild } from "@angular/core";
import { FormsModule } from "@angular/forms";
import { BaseHttpService } from "@app/core/services/http/base-http.service";
import { AppTableComponent } from "@app/shared/components/table/app-table/app-table.component";
import { AppTableConfig } from "@app/shared/components/table/table";
import { NzCardModule } from "ng-zorro-antd/card";
import { NzInputModule } from "ng-zorro-antd/input";
import { NZ_MODAL_DATA, NzModalRef } from "ng-zorro-antd/modal";
import { NzRadioGroupComponent, NzRadioModule } from "ng-zorro-antd/radio";
import { NzSelectSearchComponent } from "ng-zorro-antd/select";
import modules from "tslib";

export interface IModalData {
  id: string;
  name: string;
  path: string;
}

export interface Permission {
  id: string,
  key: string;
  description: string;
  required?: 'optional' | 'required' | 'none';
  permissionId?: string;
  sort?: number;
  optional: boolean
}

declare interface MenuPermission {
  id: string;
  menuId: string;
  permission: string;
  optional: boolean
}

@Component({
  templateUrl: 'menu-permission.html',
  imports: [
    NzCardModule,
    AppTableComponent,
    NzRadioModule,
    FormsModule,
    NzInputModule,
  ],
  standalone: true
})

export class MenuPermissionComponent implements OnInit {
  readonly #modal = inject(NzModalRef);
  readonly nzModalData: IModalData = inject(NZ_MODAL_DATA);
  permissionChooseTpl = viewChild.required("permissionChooseTpl");
  http = inject(BaseHttpService);
  menuId = input(this.nzModalData.id);
  allPermissions = signal([] as Permission[]);
  menuPermissoins = signal([] as MenuPermission[]);

  permissions = signal<Permission[]>([]);


  readonly searchValue = signal('');
  tableConfig = computed(() => {
    return {
      yScroll: 400,
      columns: [
        { name: '模块', dataIndex: 'module' },
        { name: '子模块', dataIndex: 'subModule' },
        // { name: '权限', dataIndex: 'permission' },
        { name: '权限', dataIndex: 'description' },
        { name: '是否需要', tdTemplate: this.permissionChooseTpl(), width: 300 },
      ]
    } as AppTableConfig
  });
  constructor() {
    effect(() => {
      let menuId = this.menuId();
      this.loadMenuPermissions();
    });

    effect(() => {
      let menuPermissions = this.menuPermissoins();
      let allPermissions = this.allPermissions();

      let permissions = allPermissions.map(p => {

        let modules = p.key.split(":");
        let permission = {
          ...p,
          module: modules[0],
          subModule: modules[1],
          key: p.key,
          description: p.description,
        }
        let menu = menuPermissions.find(mp => mp.permission == permission.key);
        if (menu) {
          permission.required = menu.optional ? 'optional' : 'required';
                 permission.sort = permission.required == 'required' ? 0 : 1;
        } else {
          permission.required = 'none';
           permission.sort = 100;
        }
        return permission;
      }).sort((a, b) => {
        let sort = (a.sort! - b.sort!);
        if (sort != 0) {
          return sort;
        }
        return a.key.localeCompare(b.key);
      });
      this.permissions.set(permissions);
    });
  }


  filterAndSortedMenus = computed(() => {
    let searchValue = this.searchValue().toLocaleLowerCase();
    return this.permissions().filter((p) => {
      return p.key.toLocaleLowerCase().includes(searchValue) || p.description.toLocaleLowerCase().includes(searchValue);
    }).sort((a, b) => {
        let sort = (a.sort! - b.sort!);
        if (sort != 0) {
          return sort;
        }
        return a.key.localeCompare(b.key);
      });
  })





  private loadMenuPermissions() {
    this.http.get<any>(`system/menu/${this.menuId()}`).subscribe(res => {
      this.menuPermissoins.set(res.permissions || []);
    });
  }

  ngOnInit(): void {
    let path = this.nzModalData.path;
    let split = path.split("/");
    let searchValue = split[2] + ":" + split[3];
    this.searchValue.set(searchValue);
    this.http.get<any>(`common/permission`).subscribe(res => {
      this.allPermissions.set(res.content);
    })
  }


  save(){
    let permissions = this.permissions().filter(p=>p.required != 'none').map(p=>({
      permission: p.key,
      optional: p.required == 'optional'
    }));

    this.http.put(`system/menu/${this.menuId()}`,{
      id: this.menuId(),
      permissions: permissions
    }).subscribe(() => {
      this.#modal.close(true);
    });
  }

}