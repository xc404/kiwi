import { Component, ChangeDetectionStrategy, Type } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';

import { FieldType, FieldTypeConfig, FormlyFieldConfig, FormlyAttributes } from '@ngx-formly/core';
import { FormlyFieldProps } from '@ngx-formly/ng-zorro-antd/form-field';

import { NzCheckboxModule } from 'ng-zorro-antd/checkbox';
import { NzInputModule } from 'ng-zorro-antd/input';

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
    <label nz-checkbox [formControl]="formControl" [formlyAttributes]="field" [nzIndeterminate]="props.indeterminate" (ngModelChange)="props.change && props.change(field, $event)">
      @if (props.showRightLabel) {
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
      showRightLabel: false
    }
  };
}
