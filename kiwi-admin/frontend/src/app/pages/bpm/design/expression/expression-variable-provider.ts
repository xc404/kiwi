import BaseViewer from 'bpmn-js/lib/BaseViewer';
import { Element } from 'bpmn-js/lib/model/Types';
import { ExpressionMethod, ExpressionVariable } from './expression-variable';

export interface ExpressionVariableProviderContext {
  readonly bpmnModeler: BaseViewer;
  readonly currentElement: Element;
  addVariable(variable: ExpressionVariable): void;
  addMethod(method: ExpressionMethod): void;
}

export interface ExpressionVariableProvider {
  readonly id: string;
  provide(context: ExpressionVariableProviderContext): void;
}
