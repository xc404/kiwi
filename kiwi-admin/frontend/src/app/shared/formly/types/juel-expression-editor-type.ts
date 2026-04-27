import { Component } from '@angular/core';
import { FieldType, FieldTypeConfig, FormlyFieldProps } from '@ngx-formly/core';
import { JuelExpressionEditorComponent } from '@app/shared/components/juel-expression-editor/juel-expression-editor.component';
import { SpelVariableSuggestion } from '@app/pages/bpm/design/expression/bpm-spel-variable-context';

interface JuelExpressionProps extends FormlyFieldProps {
  /** 与 SpEL 编辑器共用：图中引用变量 + 上游输出（Camunda 仍用 ${name} 引用） */
  spelVariables?: SpelVariableSuggestion[];
}

@Component({
  selector: 'juel-expression-editor-type',
  standalone: true,
  imports: [JuelExpressionEditorComponent],
  template: `
    <app-juel-expression-editor
      [value]="formControl.value"
      (valueChange)="onValueChange($event)"
      [readonly]="!!field.props.readonly"
      [variables]="variables"
    />
  `,
})
export class JuelExpressionEditorType extends FieldType<FieldTypeConfig<JuelExpressionProps>> {
  get variables(): SpelVariableSuggestion[] {
    const v = this.field.props?.spelVariables;
    return Array.isArray(v) ? v : [];
  }

  onValueChange(text: string): void {
    this.formControl.setValue(text, { emitEvent: true });
    this.formControl.markAsDirty();
    this.formControl.markAsTouched();
  }
}
