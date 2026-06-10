import { AfterViewInit, Component, computed, forwardRef, inject, input, OnInit, output, signal, TemplateRef, viewChild } from '@angular/core';
import { ControlValueAccessor, FormsModule, NG_VALUE_ACCESSOR, ReactiveFormsModule } from '@angular/forms';
import { map } from 'rxjs';

import { BaseHttpService } from '@app/core/services/http/base-http.service';
import { TreeUtils } from '@app/utils/treeUtils';

import { NgStyleInterface, NzSizeLDSType, NzStatus, NzVariant } from 'ng-zorro-antd/core/types';
import { NzInputModule } from 'ng-zorro-antd/input';
import { NzTreeComponent, NzTreeNode, NzTreeNodeOptions } from 'ng-zorro-antd/tree';
import { NzPlacementType, NzTreeSelectComponent, NzTreeSelectModule } from 'ng-zorro-antd/tree-select';

export interface NzTreeSelectorOptions {
  nzId: string | null;
  nzAllowClear: boolean;
  nzShowExpand: boolean;
  nzShowLine: boolean;
  nzDropdownMatchSelectWidth: boolean;
  nzCheckable: boolean;
  nzHideUnMatched: boolean;
  nzShowIcon: boolean;
  nzShowSearch: boolean;
  nzDisabled: boolean;
  nzAsyncData: boolean;
  nzMultiple: boolean;
  nzDefaultExpandAll: boolean;
  nzCheckStrictly: boolean;
  nzVirtualItemSize: number;
  nzVirtualMaxBufferPx: number;
  nzVirtualMinBufferPx: number;
  nzVirtualHeight: string | null;
  nzExpandedIcon?: TemplateRef<{
    $implicit: NzTreeNode;
    origin: NzTreeNodeOptions;
  }>;
  nzNotFoundContent?: string | TemplateRef<void>;
  nzNodes: NzTreeNodeOptions[] | NzTreeNode[];
  nzOpen: boolean;
  nzSize: NzSizeLDSType;
  nzVariant: NzVariant;
  nzPlaceHolder: string;
  nzDropdownStyle: NgStyleInterface | null;
  nzDropdownClassName?: string;
  nzBackdrop: boolean;
  nzStatus: NzStatus;
  nzPlacement: NzPlacementType;
}

@Component({
  selector: 'biz-tree-select',
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => BizTreeSelect),
      multi: true
    }
  ],
  template: `
    <nz-tree-select
      #nzTree
      [formControl]="formControl()"
      [nzAllowClear]="nzTreeOptions().nzAllowClear"
      [nzAsyncData]="nzTreeOptions().nzAsyncData"
      [nzBackdrop]="nzTreeOptions().nzBackdrop"
      [nzCheckable]="nzTreeOptions().nzCheckable"
      [nzCheckStrictly]="nzTreeOptions().nzCheckStrictly"
      [nzDefaultExpandAll]="nzTreeOptions().nzDefaultExpandAll"
      [nzDisabled]="nzTreeOptions().nzDisabled"
      [nzDropdownClassName]="nzTreeOptions().nzDropdownClassName"
      [nzDropdownMatchSelectWidth]="nzTreeOptions().nzDropdownMatchSelectWidth"
      [nzDropdownStyle]="nzTreeOptions().nzDropdownStyle"
      [nzExpandedIcon]="nzTreeOptions().nzExpandedIcon"
      [nzHideUnMatched]="nzTreeOptions().nzHideUnMatched"
      [nzId]="nzTreeOptions().nzId"
      [nzMultiple]="nzTreeOptions().nzMultiple"
      [nzNodes]="nodes()"
      [nzNotFoundContent]="nzTreeOptions().nzNotFoundContent"
      [nzOpen]="nzTreeOptions().nzOpen"
      [nzPlaceHolder]="nzTreeOptions().nzPlaceHolder"
      [nzPlacement]="nzTreeOptions().nzPlacement"
      [nzShowExpand]="nzTreeOptions().nzShowExpand || true"
      [nzShowIcon]="nzTreeOptions().nzShowIcon"
      [nzShowLine]="nzTreeOptions().nzShowLine"
      [nzShowSearch]="nzTreeOptions().nzShowSearch"
      [nzSize]="nzTreeOptions().nzSize"
      [nzStatus]="nzTreeOptions().nzStatus"
      [nzVariant]="nzTreeOptions().nzVariant"
      [nzVirtualHeight]="nzTreeOptions().nzVirtualHeight"
      [nzVirtualItemSize]="nzTreeOptions().nzVirtualItemSize"
      [nzVirtualMaxBufferPx]="nzTreeOptions().nzVirtualMaxBufferPx"
      [nzVirtualMinBufferPx]="nzTreeOptions().nzVirtualMinBufferPx"
      (update)="update.emit($event)"
    >
    </nz-tree-select>
  `,
  imports: [NzInputModule, ReactiveFormsModule, NzTreeSelectModule, FormsModule]
})
export class BizTreeSelect implements ControlValueAccessor, AfterViewInit, OnInit {
  http = inject(BaseHttpService);

  tree = viewChild.required<NzTreeComponent>('nzTree');

  root = input<any>({
    id: '0',
    name: '根节点',
    expanded: true
  });

  _root = signal<NzTreeNodeOptions>(null!);

  nzTree = viewChild<NzTreeSelectComponent>('nzTree');

  showRoot = input(true);

  groupCode = input.required<string>();

  extraParams = input({} as any);

  formControl = input<any>();

  readonly update = output<unknown>();

  idProperty = input('id');

  nameProperty = input('name');

  childrenProperty = input('children');

  nzTreeOptions = input<NzTreeSelectorOptions>({} as NzTreeSelectorOptions);

  nodes = computed(() => {
    console.log('compute nodes for tree select');
    const root = this._root();
    if (this.showRoot()) {
      return [root];
    }
    return root.children || [];
  });

  writeValue(obj: any): void {
    this.nzTree()?.writeValue(obj);
  }
  registerOnChange(fn: any): void {
    this.nzTree()?.registerOnChange(fn);
  }

  registerOnTouched(fn: any): void {
    this.nzTree()?.registerOnTouched(fn);
  }
  setDisabledState?(isDisabled: boolean): void {
    this.nzTree()?.setDisabledState(isDisabled);
  }

  loadChildren(node: any): void {
    const loadAll = this.nzTreeOptions().nzAsyncData ? false : true;
    const id = node.id || node.key;
    this.http
      .get<any>(`common/tree/${this.groupCode()}/${id}`, { loadAll, ...this.extraParams() })
      .pipe(
        map(res => {
          let nodes = res.content;
          nodes = TreeUtils.convertToTreeNode(nodes, this.idProperty(), this.nameProperty(), this.childrenProperty());
          return nodes;
        })
      )
      .subscribe(nodes => {
        if (loadAll) {
          this._root.update(root => {
            root.children = nodes;
            return root;
          });
        } else {
          node.clearChildren();
          node.addChildren(nodes);
        }
      });
  }

  ngOnInit(): void {
    this._root.set(TreeUtils.convertToTreeNode([this.root()], this.idProperty(), this.nameProperty(), this.childrenProperty())[0]);
    this.loadChildren(this._root());
  }

  ngAfterViewInit(): void {}
}
