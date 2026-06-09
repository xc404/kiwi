import { Component } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';

import { FieldType, FieldTypeConfig, FormlyFieldProps } from '@ngx-formly/core';
import { FormlyFieldSelectProps } from '@ngx-formly/core/select';
import { BizTreeSelect, NzTreeSelectorOptions } from '@shared/components/common-tree-selector/biz-tree-seletor';

import { NzInputModule } from 'ng-zorro-antd/input';
import { NzTreeSelectModule } from 'ng-zorro-antd/tree-select';

interface SelectProps extends FormlyFieldProps, FormlyFieldSelectProps, NzTreeSelectorOptions {
  groupCode: string;
  extraParams?: Record<string, any>;
  idProperty?: string;
  nameProperty?: string;
  childrenProperty?: string;
  rootNode?: { id: string; name: string };
}

@Component({
  template: `
    <biz-tree-select
      [childrenProperty]="field.props.childrenProperty || 'children'"
      [extraParams]="field.props.extraParams"
      [formControl]="formControl"
      [groupCode]="field.props.groupCode"
      [idProperty]="field.props.idProperty || 'id'"
      [nameProperty]="field.props.nameProperty || 'name'"
      [nzTreeOptions]="field.props"
      [root]="field.props.rootNode || { id: '0', name: '根节点' }"
    >
    </biz-tree-select>
  `,
  imports: [NzInputModule, ReactiveFormsModule, NzTreeSelectModule, BizTreeSelect],
  standalone: true
})
export class BizTreeSelectType extends FieldType<FieldTypeConfig<SelectProps>> {}
