import { Route } from '@angular/router';
import { AfterViewInit, Component, computed, forwardRef, inject, input, OnInit, output, signal, TemplateRef, viewChild } from '@angular/core';
import { ControlValueAccessor, FormsModule, NG_VALUE_ACCESSOR, ReactiveFormsModule } from '@angular/forms';
import { BaseHttpService } from '@app/core/services/http/base-http.service';
import { TreeUtils } from '@app/utils/treeUtils';
import { NzInputModule } from 'ng-zorro-antd/input';
import { NzTreeComponent, NzTreeNode, NzTreeNodeOptions } from 'ng-zorro-antd/tree';
import { NzPlacementType, NzTreeSelectComponent, NzTreeSelectModule } from 'ng-zorro-antd/tree-select';
import { expand, map } from 'rxjs';
import { NgStyleInterface, NzSizeLDSType, NzStatus, NzVariant } from 'ng-zorro-antd/core/types';


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
    multi: true,
  },
],
    template: `
        <nz-tree-select
        #nzTree
        [nzNodes]="nodes()"
        [nzShowSearch] = "nzTreeOptions().nzShowSearch"
        [nzPlaceHolder]="nzTreeOptions().nzPlaceHolder"
        [nzDropdownStyle]="nzTreeOptions().nzDropdownStyle"
        [nzDropdownClassName]="nzTreeOptions().nzDropdownClassName"
        [nzBackdrop]="nzTreeOptions().nzBackdrop"
        [nzSize]="nzTreeOptions().nzSize"   
        [nzVariant]="nzTreeOptions().nzVariant"
        [nzId]="nzTreeOptions().nzId"
        [nzAllowClear]="nzTreeOptions().nzAllowClear"
        [nzShowExpand]="nzTreeOptions().nzShowExpand||true"
        [nzShowLine]="nzTreeOptions().nzShowLine"
        [nzDropdownMatchSelectWidth]="nzTreeOptions().nzDropdownMatchSelectWidth"
        [nzCheckable]="nzTreeOptions().nzCheckable"
        [nzHideUnMatched]="nzTreeOptions().nzHideUnMatched"
        [nzShowIcon]="nzTreeOptions().nzShowIcon"
        [nzDisabled]="nzTreeOptions().nzDisabled"
        [nzAsyncData]="nzTreeOptions().nzAsyncData"
        [nzMultiple]="nzTreeOptions().nzMultiple"
        [nzDefaultExpandAll]="nzTreeOptions().nzDefaultExpandAll"
        [nzCheckStrictly]="nzTreeOptions().nzCheckStrictly"
        [nzVirtualItemSize]="nzTreeOptions().nzVirtualItemSize"
        [nzVirtualMaxBufferPx]="nzTreeOptions().nzVirtualMaxBufferPx"
        [nzVirtualMinBufferPx]="nzTreeOptions().nzVirtualMinBufferPx"
        [nzVirtualHeight]="nzTreeOptions().nzVirtualHeight"
        [nzExpandedIcon]="nzTreeOptions().nzExpandedIcon"
        [nzNotFoundContent]="nzTreeOptions().nzNotFoundContent"
        [nzOpen]="nzTreeOptions().nzOpen"
        [nzPlacement]="nzTreeOptions().nzPlacement"
        [nzStatus]="nzTreeOptions().nzStatus"
        [formControl]="formControl()"
        (update)="update.emit($event)"
        >
        </nz-tree-select>
  `,
    imports: [NzInputModule, ReactiveFormsModule, NzTreeSelectModule, FormsModule],
})
export class BizTreeSelect implements  ControlValueAccessor, AfterViewInit , OnInit{

    
    

    http = inject(BaseHttpService);

    tree = viewChild.required<NzTreeComponent>("nzTree");

    root = input<any>({
        id:"0",
        name: "根节点",
        expanded: true,
    });

    _root = signal<NzTreeNodeOptions>(null!);

    nzTree = viewChild<NzTreeSelectComponent>('nzTree');


    showRoot = input(true);

    groupCode = input.required<string>();

    extraParams = input({} as any);


    formControl = input<any>();

    update = output<any>();


    idProperty = input('id');

    nameProperty = input('name');

    childrenProperty = input('children');

    nzTreeOptions = input<NzTreeSelectorOptions>({} as NzTreeSelectorOptions);



    nodes = computed(() => {
        console.log('compute nodes for tree select');
        let root = this._root();
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




    loadChildren(node:any): void {
        const loadAll = this.nzTreeOptions().nzAsyncData ? false : true;
        let id = node.id || node.key;
        this.http.get<any>('common/tree/' + this.groupCode() + '/' + id, { loadAll, ...this.extraParams(), }).pipe(
            map(res => {
                let nodes = res.content;
                nodes = TreeUtils.convertToTreeNode(nodes, this.idProperty(), this.nameProperty(), this.childrenProperty());
                return nodes;
            })
        ).subscribe(nodes => {
            
            if(loadAll){
               this._root.update(root=>{
                    root.children = nodes;
                    return root;
               });
            }else{
                node.clearChildren();
                node.addChildren(nodes);
            }
        });
    }

    ngOnInit(): void {
        this._root.set(TreeUtils.convertToTreeNode([this.root()], this.idProperty(), this.nameProperty(), this.childrenProperty())[0]);
       this.loadChildren(this._root());
    }

    ngAfterViewInit(): void {
    
    }
  
}
