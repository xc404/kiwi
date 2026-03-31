import { Component, inject, OnInit, signal } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { BaseHttpService } from '@app/core/services/http/base-http.service';
import { FieldType, FieldTypeConfig, FormlyAttributes, FormlyFieldProps } from '@ngx-formly/core';
import { FormlyFieldSelectProps } from '@ngx-formly/core/select';
import { NzInputModule } from 'ng-zorro-antd/input';
import { NzTreeNodeOptions } from 'ng-zorro-antd/tree';
import { NzTreeSelectModule } from 'ng-zorro-antd/tree-select';

interface SelectProps extends FormlyFieldProps, FormlyFieldSelectProps {
  includeLeaf: boolean
}



@Component({
  selector: 'app-menu-sel',
  template: `
        <nz-tree-select
       [formlyAttributes]="field"
      [nzNodes]="menus()"
      nzShowSearch
      [formControl]="formControl" 
    ></nz-tree-select>
  `,
  imports: [FormlyAttributes, NzInputModule, ReactiveFormsModule, NzTreeSelectModule],
})
export class MenuSelectType extends FieldType<FieldTypeConfig<SelectProps>> implements OnInit {

  http = inject(BaseHttpService);

  selIconVisible = false;

  menus = signal([] as NzTreeNodeOptions[]);
  seledIcon(icon: string): void {
    this.formControl.setValue(icon);
  }

  ngOnInit(): void {
    this.http.get<any>('system/menu').subscribe(res => {
      let menu = res.content as any[];
      menu = menu.map(m => {
        return this.convertMenu(m);
      }).filter(m => {
        return m;
      });

      this.menus.set([{
        title: '菜单列表',
        key: '0',
        children: menu,
        expanded: true
      }]);
      this.formControl.markAsUntouched();
    });
  }

  convertMenu(menu: any): any {
    if (this.field.props?.includeLeaf != true && menu.menuType == 'C') {
      return null;
    }
    if (menu.children) {
      menu.children = menu.children.map((item: any) => {
        return this.convertMenu(item);
      }).filter((item: any) => {
        return item;
      });
    }

    let hasChildren = menu.children && menu.children.length > 0;
    return { ...menu, title: menu.name, key: menu.id, isLeaf: !hasChildren };
  }
}
