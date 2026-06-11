// panel-wrapper.component.ts
import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component } from '@angular/core';

import { FieldWrapper, FormlyFieldConfig, FormlyValidationMessage, FormlyFieldProps as CoreFormlyFieldProps } from '@ngx-formly/core';

import { NzFormModule } from 'ng-zorro-antd/form';
import { NzGridModule } from 'ng-zorro-antd/grid';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzLayoutModule } from 'ng-zorro-antd/layout';
import { NzTooltipModule } from 'ng-zorro-antd/tooltip';

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
      @if (props.label && props.hideLabel !== true) {
        <nz-form-label class="app-form-label" nzLabelWrap [nzFor]="id" [nzRequired]="props.required && props.hideRequiredMarker !== true" [nzXs]="24">
          {{ props.label }}
        </nz-form-label>
      }
      <nz-form-control class="app-form-control" [nzErrorTip]="errorTpl" [nzExtra]="props.hideOuterDescription ? undefined : props.description" [nzValidateStatus]="errorState" [nzXs]="24">
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
    <nz-form-item class="app-formly-vertical-item">
      @if (props.label && props.hideLabel !== true) {
        <div class="app-formly-vertical-item__label-row">
          <nz-form-label class="app-formly-vertical-item__label" nzNoColon="true" [nzFor]="id" [nzRequired]="props.required && props.hideRequiredMarker !== true">
            {{ props.label }}
          </nz-form-label>
          @if (descriptionTooltip) {
            <span class="app-formly-vertical-item__tip" nz-icon nz-tooltip nzTheme="outline" nzType="question-circle" [nzTooltipTitle]="descriptionTooltip"></span>
          }
        </div>
      }
      <nz-form-control class="app-formly-vertical-item__control" [nzErrorTip]="errorTpl" [nzValidateStatus]="errorState">
        <ng-container #fieldComponent></ng-container>
        <ng-template #errorTpl let-control>
          <formly-validation-message [field]="field"></formly-validation-message>
        </ng-template>
      </nz-form-control>
    </nz-form-item>
  `,
  imports: [NzFormModule, CommonModule, NzGridModule, FormlyValidationMessage, NzLayoutModule, NzIconModule, NzTooltipModule]
})
export class VerticalFormFieldWrapper extends FieldWrapper<FormlyFieldConfig<FormlyFieldProps>> {
  /** 标签右侧提示：字符串说明且未禁止外层说明时展示（与原先 nzExtra 展示条件一致，改为 tooltip） */
  get descriptionTooltip(): string | undefined {
    if (this.props.hideOuterDescription) {
      return undefined;
    }
    const d = this.props.description;
    if (d == null || typeof d !== 'string') {
      return undefined;
    }
    const s = d.trim();
    return s.length > 0 ? s : undefined;
  }

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
