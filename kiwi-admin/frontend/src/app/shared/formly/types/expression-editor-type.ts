import { Component } from '@angular/core';

import { SpelVariableSuggestion } from '@app/pages/bpm/design/expression/expression-variable';
import { JuelExpressionEditorComponent } from '@app/shared/components/juel-expression-editor/juel-expression-editor.component';
import { SpelExpressionEditorComponent } from '@app/shared/components/spel-expression-editor/spel-expression-editor.component';
import { FieldType, FieldTypeConfig, FormlyFieldProps } from '@ngx-formly/core';

export type ExpressionDialect = 'spel' | 'juel';

interface ExpressionProps extends FormlyFieldProps {
  spelVariables?: SpelVariableSuggestion[];
  expressionDialect?: ExpressionDialect;
}

@Component({
  selector: 'expression-editor-type',
  standalone: true,
  imports: [SpelExpressionEditorComponent, JuelExpressionEditorComponent],
  template: `
    @if (dialect === 'juel') {
      <app-juel-expression-editor [readonly]="!!field.props.readonly" [value]="formControl.value" [variables]="variables" (valueChange)="onValueChange($event)" />
    } @else {
      <app-spel-expression-editor [readonly]="!!field.props.readonly" [value]="formControl.value" [variables]="variables" (valueChange)="onValueChange($event)" />
    }
  `
})
export class ExpressionEditorType extends FieldType<FieldTypeConfig<ExpressionProps>> {
  get variables(): SpelVariableSuggestion[] {
    const v = this.field.props?.spelVariables;
    return Array.isArray(v) ? v : [];
  }

  get dialect(): ExpressionDialect {
    return this.field.props?.expressionDialect === 'juel' ? 'juel' : 'spel';
  }

  onValueChange(text: string): void {
    this.formControl.setValue(text, { emitEvent: true });
    this.formControl.markAsDirty();
    this.formControl.markAsTouched();
  }
}
