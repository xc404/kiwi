import { inject, Injectable } from '@angular/core';

import BaseViewer from 'bpmn-js/lib/BaseViewer';
import { Element } from 'bpmn-js/lib/model/Types';

import { SpelVariableSuggestion } from './expression-variable';
import { ExpressionVariableContext } from './expression-variable-context';
import { BpmnModelerUpstreamVariableProvider } from './providers/bpmn-modeler-upstream-variable-provider';

@Injectable({ providedIn: 'root' })
export class BpmExpressionVariableService {
  private readonly upstreamProvider = inject(BpmnModelerUpstreamVariableProvider);

  buildContext(bpmnModeler: BaseViewer, currentElement: Element): ExpressionVariableContext {
    return new ExpressionVariableContext().mergeFromProviders(bpmnModeler, currentElement, [this.upstreamProvider]);
  }

  buildSuggestions(bpmnModeler: BaseViewer, currentElement: Element): SpelVariableSuggestion[] {
    return this.buildContext(bpmnModeler, currentElement).toSuggestions();
  }
}
