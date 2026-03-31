import { AfterViewInit, Component, computed, inject, OnInit, output, signal, viewChild, ViewChild } from '@angular/core';
import { MatExpansionModule } from "@angular/material/expansion";
import { BaseHttpService } from '@app/core/services/http/base-http.service';
import { AppButtonConfig } from '@app/shared/components/button/app.button';
import { FormPanel } from "@app/shared/formly/panel/form-panel";
import { TreeUtils } from '@app/utils/treeUtils';
import { NzContextMenuService, NzDropdownMenuComponent, NzDropDownModule } from 'ng-zorro-antd/dropdown';
import { NzModalService } from 'ng-zorro-antd/modal';
import { NzFormatEmitEvent, NzTreeComponent, NzTreeNode } from 'ng-zorro-antd/tree';
import { map } from 'rxjs/internal/operators/map';
import { ProcessDesignService } from '../service/process-degisn.service';

@Component({
  selector: 'bpm-explorer',
  templateUrl: './explorer.component.html',
  styleUrls: ['explorer.component.scss'],
  imports: [NzTreeComponent, NzDropDownModule,  MatExpansionModule]
})
export class BpmExplorerComponent implements OnInit, AfterViewInit {

  constructor() { }


  fileOpen = output<any>();

  root = {
    key: '0',
    title: '用户目录',
    name: '用户目录',
    id: '0',
    children: []
  };

  nodes = computed(() => {
    return [this.root];
  });

  http = inject(BaseHttpService);
  modalService = inject(NzModalService);

  processDesignService = inject(ProcessDesignService);

  addBtn: AppButtonConfig = {
    icon: 'plus',
    nzSize: 'small'
  }
  @ViewChild('bpmTree') bpmTree: any;
  selectedNode = signal<NzTreeNode | null>(null);
  model = signal({});
  nzContextMenuService = inject(NzContextMenuService);

  treeDropMenu = viewChild.required<NzDropdownMenuComponent>('treeMenu');



  loadChildren(node: NzTreeNode) {
    return this.http.get(`/bpm/folder/files`).pipe(map((res: any) => {
      return res.content;
    })).subscribe(data => {
      const children = TreeUtils.convertToTreeNode(data);
      node.clearChildren();
      node.addChildren(children);
    });
  }


  contextMenu(event: NzFormatEmitEvent) {
    this.selectedNode.set(event.node!);
    this.nzContextMenuService.create(event.event!, this.treeDropMenu());
  }

  addFile() {
     let modal: any = this.modalService.create({
      nzTitle: '新建文件',
      nzContent: NameForm,
      nzOnOk: (instance) => {
        const parentNode = this.selectedNode();
        const name = instance.getName();
        this.processDesignService.create(parentNode?.key||'0', name).subscribe(res => {
          console.log('创建成功', res);
        });
      }
    });

  }

  addFolder() {

    let modal: any = this.modalService.create({
      nzTitle: '新建文件夹',
      nzContent: NameForm,
      nzOnOk: (instance) => {
        const parentNode = this.selectedNode();
        const folderName = instance.getName();
        this.http.post(`/bpm/folder`, { name: folderName, parentId: parentNode ? parentNode.key : '0' }).subscribe(res => {
          console.log('创建成功', res);
        });
      }
    });

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
    this.http.delete(`/bpm/${currentNode.key}`).subscribe(res => {
      console.log('删除成功', res);
    });
  }
  ngOnInit() {
  }

  ngAfterViewInit(): void {
    setTimeout(() => {
      this.bpmTree.getTreeNodeByKey('0')!.setExpanded(true);
      this.loadChildren(this.bpmTree.getTreeNodeByKey('0')!);
    });
  }
}



@Component({
  template: `
    
    <app-formly-panel [model]="model" [fields]="formConfig"></app-formly-panel>
  `,
  imports: [FormPanel],
  standalone: true
})
export class NameForm {


  model = {
    name: ''
  };
  formConfig = [
    {
      key: 'name',
      type: 'input',
      templateOptions: {
        label: '名称',
        required: true,
      },
    }
  ];

  getName() {
    return this.model['name'];
  }
}