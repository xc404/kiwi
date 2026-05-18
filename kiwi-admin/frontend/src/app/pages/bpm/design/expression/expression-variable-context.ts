import BaseViewer from 'bpmn-js/lib/BaseViewer';
import { Element } from 'bpmn-js/lib/model/Types';
import {
  ExpressionMethod,
  ExpressionVariable,
  ExpressionVariableKind,
  SpelVariableSuggestion,
  toSpelVariableSuggestions,
} from './expression-variable';
import {
  ExpressionVariableProvider,
  ExpressionVariableProviderContext,
} from './expression-variable-provider';

const KIND_PRIORITY: Record<ExpressionVariableKind, number> = {
  upstreamOutput: 3,
  declaredOutput: 2,
  upstreamInput: 1,
};

class ProviderContextImpl implements ExpressionVariableProviderContext {
  constructor(
    readonly bpmnModeler: BaseViewer,
    readonly currentElement: Element,
    private readonly onVariable: (v: ExpressionVariable) => void,
    private readonly onMethod: (m: ExpressionMethod) => void,
  ) {}

  addVariable(variable: ExpressionVariable): void {
    this.onVariable(variable);
  }

  addMethod(method: ExpressionMethod): void {
    this.onMethod(method);
  }
}

export class ExpressionVariableContext {
  private readonly variables = new Map<string, ExpressionVariable>();
  private readonly methods: ExpressionMethod[] = [];

  mergeFromProviders(
    bpmnModeler: BaseViewer,
    currentElement: Element,
    providers: ExpressionVariableProvider[],
  ): this {
    for (const provider of providers) {
      const ctx = new ProviderContextImpl(
        bpmnModeler,
        currentElement,
        (v) => this.mergeVariable(v),
        (m) => this.methods.push(m),
      );
      provider.provide(ctx);
    }
    return this;
  }

  getVariables(): ExpressionVariable[] {
    return [...this.variables.values()].sort((a, b) => a.key.localeCompare(b.key));
  }

  getMethods(): ExpressionMethod[] {
    return [...this.methods];
  }

  toSuggestions(): SpelVariableSuggestion[] {
    return toSpelVariableSuggestions(this.getVariables());
  }

  private mergeVariable(variable: ExpressionVariable): void {
    const key = variable.key.trim();
    if (!key) {
      return;
    }
    const normalized: ExpressionVariable = { ...variable, key };
    const existing = this.variables.get(key);
    if (!existing || KIND_PRIORITY[normalized.kind] > KIND_PRIORITY[existing.kind]) {
      this.variables.set(key, normalized);
    }
  }
}
