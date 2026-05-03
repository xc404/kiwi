import { Component } from '@angular/core';
import { FieldType, FieldTypeConfig, FormlyFieldProps } from '@ngx-formly/core';
import { AssignmentsEditorComponent } from '@app/shared/components/assignments-editor/assignments-editor.component';

interface AssignmentsProps extends FormlyFieldProps {
  /** 来自属性面板：运行时变量列表（含 name） */
  variables?: Array<{ name?: string | null; [key: string]: unknown }>;
}

@Component({
  selector: 'assignments-editor-type',
  standalone: true,
  imports: [AssignmentsEditorComponent],
  template: `
    <app-assignments-editor
      [value]="formControl.value"
      (valueChange)="onValueChange($event)"
      [readonly]="!!field.props.readonly"
      [variables]="variables"
    />
  `,
})
export class AssignmentsEditorType extends FieldType<FieldTypeConfig<AssignmentsProps>> {
  get variables(): Array<{ name?: string | null; [key: string]: unknown }> {
    const v = this.field.props?.variables;
    return Array.isArray(v) ? v : [];
  }

  onValueChange(json: string): void {
    this.formControl.setValue(json, { emitEvent: true });
    this.formControl.markAsDirty();
    this.formControl.markAsTouched();
  }
}
