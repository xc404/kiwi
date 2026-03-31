import { Component, ChangeDetectionStrategy, Type } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { FieldType, FieldTypeConfig, FormlyFieldConfig } from '@ngx-formly/core';
import { FormlyFieldProps } from '@ngx-formly/ng-zorro-antd/form-field';
import { NzCheckboxModule } from 'ng-zorro-antd/checkbox';
import { NzInputModule } from 'ng-zorro-antd/input';
import { FormlyAttributes } from '@ngx-formly/core';

interface CheckboxProps extends FormlyFieldProps {
    indeterminate?: boolean;
    showRightLabel?: boolean;
}

export interface FormlyCheckboxFieldConfig extends FormlyFieldConfig<CheckboxProps> {
    type: 'checkbox' | Type<FormlyFieldCheckbox>;

}

@Component({
    selector: 'app-field-checkbox',
    template: `
    <label
      nz-checkbox
      [nzIndeterminate]="props.indeterminate"
      [formControl]="formControl"
      (ngModelChange)="props.change && props.change(field, $event)",
         [formlyAttributes]="field"
    >
    @if(props.showRightLabel){

        {{ props.label }}
    }
    </label>
  `,
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [NzInputModule, ReactiveFormsModule, NzCheckboxModule, FormlyAttributes],
    standalone: true
})
export class FormlyFieldCheckbox extends FieldType<FieldTypeConfig<CheckboxProps>> {
    override defaultOptions = {
        props: {
            indeterminate: false,
            showRightLabel: false,
        },
    };
}