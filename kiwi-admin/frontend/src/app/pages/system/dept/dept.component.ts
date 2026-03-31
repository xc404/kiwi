import { AfterViewInit, ChangeDetectionStrategy, Component, computed, inject, OnInit, signal, viewChild } from '@angular/core';
import { FormGroup, FormsModule } from '@angular/forms';
import { map } from 'rxjs/operators';

import { Dept } from '@services/system/dept.service';

import { BaseHttpService } from '@app/core/services/http/base-http.service';
import { AppButton, AppButtonConfig } from "@app/shared/components/button/app.button";
import { FormPanel } from "@app/shared/formly/panel/form-panel";
import { TreeUtils } from '@app/utils/treeUtils';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzCardModule } from 'ng-zorro-antd/card';
import { NzSafeAny } from 'ng-zorro-antd/core/types';
import { NzWaveModule } from 'ng-zorro-antd/core/wave';
import { NzContextMenuService, NzDropdownMenuComponent, NzDropDownModule } from "ng-zorro-antd/dropdown";
import { NzFormModule } from 'ng-zorro-antd/form';
import { NzGridModule } from 'ng-zorro-antd/grid';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzInputModule } from 'ng-zorro-antd/input';
import { NzSelectModule } from 'ng-zorro-antd/select';
import { NzTagModule } from 'ng-zorro-antd/tag';
import { NzFormatEmitEvent, NzTreeComponent, NzTreeModule, NzTreeNode } from 'ng-zorro-antd/tree';

interface SearchParam {
  departmentName: string;
  state: boolean;
}

@Component({
  selector: 'app-dept',
  templateUrl: './dept.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    NzCardModule,
    FormsModule,
    NzFormModule,
    NzGridModule,
    NzInputModule,
    NzSelectModule,
    NzButtonModule,
    NzWaveModule,
    NzIconModule,
    NzTagModule,
    NzTreeModule,
    FormPanel,
    NzDropDownModule
  ]
})
export class DeptComponent implements OnInit, AfterViewInit {


  httpService = inject(BaseHttpService);
  nzContextMenuService = inject(NzContextMenuService);

  deptTree = viewChild.required<NzTreeComponent>('deptTree');

  // rootNode = {{
  //   key: '0',
  //   title: '总部门',
  //   children: []
  // }}

  root = {
    key: '0',
    title: '总部门',
    name: '总部门',
    id: '0',
    children: []
  };

  nodes = computed(() => {
    return [this.root];
  });

  fields = [
    {
      key: 'name',
      label: '部门名称',
      type: 'input',
      defaultValue: '',
      props: {
        label: '部门名称',
        placeholder: '请输入部门名称'
      }
    },
    {
      key: 'parentId',
      label: '上级部门',
      type: 'biz-tree-select',
      defaultValue: '0',
      props: {
        label: '上级部门',
        placeholder: '请选择上级部门',
        rootNode : this.root,
        showRoot: true,
        groupCode: 'sys-dept',
      }
    },
    {
      key: 'sort',
      label: '排序',
      type: 'input',
      defaultValue: 10,
      props: {
        label: '排序',
        placeholder: '请输入排序'
      }
    }
  ];
  model = signal<any>({});



  form = new FormGroup({});

  selectedNode = signal<NzTreeNode | null>(null);

  addBtn: AppButtonConfig = {
    icon: 'plus',
    nzSize: 'small',
    handler: () => {
      this.model.set({});
    }
  }



  ngOnInit(): void {
    // 初始加载根节点
    // this.loadChildren(this.root());
  }

  loadChildren(node: NzTreeNode) {
    let id = node.key;
    return this.httpService.get<Dept[]>(`/system/dept/${id}/children`).pipe(map((res: any) => {
      return res.content;
    })).subscribe(data => {
      const children = TreeUtils.convertToTreeNode(data);
      node.clearChildren();
      node.addChildren(children);
    });
  }

  submit() {
    if (this.model().id) {
      this.httpService.put<NzSafeAny>(`/system/dept/${this.model().id}`, this.model()).subscribe(res => {
        console.log('保存成功', res);
      });
    }
    else {

      this.httpService.post<NzSafeAny>('/system/dept', this.model()).subscribe(res => {
        console.log('保存成功', res);
      });
    }
  }

  contextMenu(event: NzFormatEmitEvent, menu: NzDropdownMenuComponent) {
    this.selectedNode.set(event.node!);
    this.nzContextMenuService.create(event.event!, menu);
  }

  addSub() {
    const parentNode = this.selectedNode();
    this.model.set({ parentId: parentNode ? parentNode.key : '0' });
  }
  editCurrent(node?: NzTreeNode) {

    // const currentNode = this.selectedNode();
    if (node) {
      this.selectedNode.set(node);
    }
    const currentNode = this.selectedNode();
    if (!currentNode) {
      return;
    }
    this.model.set({
      ...currentNode.origin,
    });
  }

  deleteCurrent() {
    const currentNode = this.selectedNode();
    if (!currentNode) {
      return;
    }
    this.httpService.delete(`/system/dept/${currentNode.key}`).subscribe(res => {
      console.log('删除成功', res);
    });
  }


  ngAfterViewInit(): void {
    setTimeout(() => {
      this.deptTree().getTreeNodeByKey('0')!.setExpanded(true);
      this.loadChildren(this.deptTree().getTreeNodeByKey('0')!);
    });
  }


}
