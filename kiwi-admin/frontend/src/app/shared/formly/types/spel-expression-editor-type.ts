import { Component } from '@angular/core';

import { SpelVariableSuggestion } from '@app/pages/bpm/design/expression/expression-variable';
import { SpelExpressionEditorComponent } from '@app/shared/components/spel-expression-editor/spel-expression-editor.component';
import { FieldType, FieldTypeConfig, FormlyFieldProps } from '@ngx-formly/core';

interface SpelExpressionProps extends FormlyFieldProps {
  /** 来自属性面板：图中引用变量 + 上游输出 */
  spelVariables?: SpelVariableSuggestion[];
}

@Component({
  selector: 'spel-expression-editor-type',
  standalone: true,
  imports: [SpelExpressionEditorComponent],
  template: ` <app-spel-expression-editor [readonly]="!!field.props.readonly" [value]="formControl.value" [variables]="variables" (valueChange)="onValueChange($event)" /> `
})
export class SpelExpressionEditorType extends FieldType<FieldTypeConfig<SpelExpressionProps>> {
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
