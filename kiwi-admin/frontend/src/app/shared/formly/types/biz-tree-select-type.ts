import { Component } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { BizTreeSelect, NzTreeSelectorOptions } from "@app/widget/common-widget/common-tree-selector/biz-tree-seletor";
import { FieldType, FieldTypeConfig, FormlyFieldProps } from '@ngx-formly/core';
import { FormlyFieldSelectProps } from '@ngx-formly/core/select';
import { NzInputModule } from 'ng-zorro-antd/input';
import { NzTreeSelectModule } from 'ng-zorro-antd/tree-select';

interface SelectProps extends FormlyFieldProps, FormlyFieldSelectProps, NzTreeSelectorOptions {
  groupCode: string;
  extraParams?: Record<string, any>;
  idProperty?: string;
  nameProperty?: string;
  childrenProperty?: string;
  rootNode?: { id: string, name: string };
}



@Component({
  template: `
       <biz-tree-select
       [groupCode]="field.props.groupCode"
       [extraParams]="field.props.extraParams"
       [idProperty]="field.props.idProperty || 'id'"
       [nameProperty]="field.props.nameProperty || 'name'"
       [childrenProperty]="field.props.childrenProperty || 'children'"
       [root]="field.props.rootNode||{id:'0',name:'根节点'}"
       [formControl]="formControl"
       [nzTreeOptions]="field.props"
       >  
</biz-tree-select>
  `,
  imports: [NzInputModule, ReactiveFormsModule, NzTreeSelectModule, BizTreeSelect],
  standalone: true
})
export class BizTreeSelectType extends FieldType<FieldTypeConfig<SelectProps>> {

}
