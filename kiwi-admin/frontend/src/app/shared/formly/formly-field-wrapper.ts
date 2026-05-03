// panel-wrapper.component.ts
import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component } from '@angular/core';
import { FieldWrapper, FormlyFieldConfig, FormlyValidationMessage, FormlyFieldProps as CoreFormlyFieldProps } from '@ngx-formly/core';
import { NzFormModule } from 'ng-zorro-antd/form';
import { NzGridModule } from 'ng-zorro-antd/grid';
import { NzLayoutModule } from 'ng-zorro-antd/layout';

export interface FormlyFieldProps extends CoreFormlyFieldProps {
  hideRequiredMarker?: boolean;
  hideLabel?: boolean;
  hideOuterDescription?: boolean;
}

@Component({
  selector: 'formly-wrapper',
  styleUrl: './formly-field-wrapper.css',
  template: `
       <nz-form-item>
        @if (props.label && (props.hideLabel !== true)) {
          <nz-form-label nzLabelWrap  class="app-form-label" [nzXs]="24" [nzRequired]="props.required && props.hideRequiredMarker !== true" [nzFor]="id">
            {{ props.label }}
          </nz-form-label>
        }
      <nz-form-control class="app-form-control" [nzXs]="24" [nzValidateStatus]="errorState" [nzErrorTip]="errorTpl" [nzExtra]="props.hideOuterDescription ? undefined : props.description">
        <ng-container #fieldComponent></ng-container>
        <ng-template #errorTpl let-control>
          <formly-validation-message [field]="field"></formly-validation-message>
        </ng-template>
      </nz-form-control>
    </nz-form-item>
`,
  imports: [NzFormModule, CommonModule, NzGridModule, FormlyValidationMessage]
})
export class HorizontalFormFieldWrapper extends FieldWrapper<FormlyFieldConfig<FormlyFieldProps>> {
  get errorState() {
    return this.showError ? 'error' : '';
  }
}


@Component({
  selector: 'formly-wrapper',
  styleUrl: './formly-field-wrapper.css',
  template: `
       <nz-form-item >
        @if (props.label && (props.hideLabel !== true)) {
          <nz-form-label nzLabelWrap  [nzRequired]="props.required && props.hideRequiredMarker !== true" [nzFor]="id">
            {{ props.label }}
          </nz-form-label>
        }
      <nz-form-control class="app-form-control"  [nzValidateStatus]="errorState" [nzErrorTip]="errorTpl" [nzExtra]="props.hideOuterDescription ? undefined : props.description">
        <ng-container #fieldComponent></ng-container>
        <ng-template #errorTpl let-control>
          <formly-validation-message [field]="field"></formly-validation-message>
        </ng-template>
      </nz-form-control>
    </nz-form-item>
`,
  imports: [NzFormModule, CommonModule, NzGridModule, FormlyValidationMessage, NzLayoutModule]
})
export class VerticalFormFieldWrapper extends FieldWrapper<FormlyFieldConfig<FormlyFieldProps>> {
  get errorState() {
    return this.showError ? 'error' : '';
  }
}



@Component({
  selector: 'column-edit-field',
  styleUrl: './formly-field-wrapper.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
      <!-- <ng-container *ngIf="props.label && (props.hideLabel !== true)">
        <nz-form-label  [nzSm]="5" [nzXs]="24" [nzRequired]="props.required && props.hideRequiredMarker !== true" [nzFor]="id">
          {{ props.label }}
        </nz-form-label>
      </ng-container> -->
        <ng-container #fieldComponent></ng-container>
        <ng-template #errorTpl let-control>
          <formly-validation-message [field]="field"></formly-validation-message>
        </ng-template>
`,
  imports: [NzFormModule, CommonModule, NzGridModule, FormlyValidationMessage]
})
export class ColumnEditField extends FieldWrapper<FormlyFieldConfig<FormlyFieldProps>> {
  get errorState() {
    return this.showError ? 'error' : '';
  }
}

