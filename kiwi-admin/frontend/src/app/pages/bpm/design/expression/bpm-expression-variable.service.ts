import { inject, Injectable } from '@angular/core';

import BaseViewer from 'bpmn-js/lib/BaseViewer';
import { Element } from 'bpmn-js/lib/model/Types';

import { BpmDesignerContextService } from '../bpm-designer-context.service';
import { SpelVariableSuggestion } from './expression-variable';
import { ExpressionVariableContext } from './expression-variable-context';
import { EXPRESSION_VARIABLE_PROVIDER_CONTRIBUTOR } from './expression-variable-provider.registry';
import { ExpressionVariableProvider } from './expression-variable-provider';

@Injectable({ providedIn: 'root' })
export class BpmExpressionVariableService {
  private readonly designerContext = inject(BpmDesignerContextService);
  private readonly contributors = inject(EXPRESSION_VARIABLE_PROVIDER_CONTRIBUTOR) as unknown as ExpressionVariableProvider[];

  buildContext(bpmnModeler: BaseViewer, currentElement: Element): ExpressionVariableContext {
    this.designerContext.catalogRevision();
    return new ExpressionVariableContext().mergeFromProviders(bpmnModeler, currentElement, this.contributors);
  }

  buildSuggestions(bpmnModeler: BaseViewer, currentElement: Element): SpelVariableSuggestion[] {
    return this.buildContext(bpmnModeler, currentElement).toSuggestions();
  }
}
