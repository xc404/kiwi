import { NgClass } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, effect } from '@angular/core';


import { NzSafeAny } from 'ng-zorro-antd/core/types';
import { NzResizableModule } from 'ng-zorro-antd/resizable';
import { NzTableModule } from 'ng-zorro-antd/table';

import { TableCell, TableHeaderCell } from '../column';
import { BaseTableComponent, TableComponentToken } from '../table';
import { TreeModel } from './tree-model';

@Component({
  selector: 'app-tree-table',
  templateUrl: './app-tree-table.component.html',
  styleUrls: ['./app-tree-table.component.less'],
  providers: [{ provide: TableComponentToken, useExisting: AppTreeTableComponent }],
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [NzTableModule, NzResizableModule, NgClass, TableHeaderCell, TableCell]
})
export class AppTreeTableComponent extends BaseTableComponent {

  constructor() {
    super();
    effect(() => {
      this.treeModel.setItems(this.tableData());
    })
  }

  treeModel = new TreeModel([]);

  override _dataList = computed(() => {
    return this.treeModel.flatternItems();
  });

  collapse(data: any, $event: boolean): void {
    this.treeModel.toggleCollapse(data.id);
  }

  isExpanded(data: any): boolean {
    return this.treeModel.isExpanded(data.id);
  }

  isItemIndeterminate(item: any): boolean {
    if (this.tableConfig().enableTreeSelection) {
      if (!item.children) {
        return false;
      }
      return item.children.some((child: any) => this.isItemSelected(child)) && !this.isChilrenSelected(item);
    }

    return false;

  }

  override isItemSelected(item: any): boolean {
    if (this.tableConfig().enableTreeSelection) {
      return super.isItemSelected(item) && this.isChilrenSelected(item);
    }
    return super.isItemSelected(item);
  }

  isChilrenSelected(item: any) {
    if (!item.children) {
      return true;
    }
    return item.children.every((child: any) => this.isItemSelected(child));
  }

  override checkFn(dataItem: NzSafeAny, isChecked: boolean): void {
    super.checkFn(dataItem, isChecked);

    if (this.tableConfig().enableTreeSelection && dataItem.children) {
      dataItem.children.forEach((child: any) => this.checkFn(child, isChecked));
    }
    this.checkParent(dataItem);
  }

  checkParent(dataItem: any){
     let parent = dataItem._parent;
      if(!parent){
          return;
      }
      if(this.isItemSelected(parent) || this.isItemIndeterminate(parent)){
        super.checkFn(parent, true);
      }else {
        super.checkFn(parent, false);
      }    

  }
}
