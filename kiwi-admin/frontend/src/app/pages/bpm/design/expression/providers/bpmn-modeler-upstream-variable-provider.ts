import { inject, Injectable } from '@angular/core';
import BaseViewer from 'bpmn-js/lib/BaseViewer';
import { Element } from 'bpmn-js/lib/model/Types';
import { ComponentService } from '../../../flow-elements/component-service';
import { ElementModel } from '../../extension/element-model';
import {
  ExpressionVariableProvider,
  ExpressionVariableProviderContext,
} from '../expression-variable-provider';
import { backwardReachable, buildReverseAdjacency } from '../process-graph';

function parameterName(param: Element): string {
  const name = (param as { get?: (key: string) => unknown }).get?.('name');
  return String(name ?? '').trim();
}

function elementLabel(el: Element): string {
  const bo = el.businessObject as { name?: string };
  const name = bo?.name?.trim();
  return name || el.id;
}

@Injectable({ providedIn: 'root' })
export class BpmnModelerUpstreamVariableProvider implements ExpressionVariableProvider {
  readonly id = 'bpmn-modeler-upstream';

  private readonly elementModel = inject(ElementModel);
  private readonly componentService = inject(ComponentService);

  provide(context: ExpressionVariableProviderContext): void {
    const { bpmnModeler, currentElement } = context;
    const reverseAdj = buildReverseAdjacency(bpmnModeler);
    const reachable = backwardReachable(reverseAdj, currentElement.id);
    const registry = bpmnModeler.get('elementRegistry') as {
      get: (id: string) => Element | undefined;
    };

    for (const nodeId of reachable) {
      if (nodeId === currentElement.id) {
        continue;
      }
      const el = registry.get(nodeId);
      if (!el || (el.type !== 'bpmn:ServiceTask' && el.type !== 'bpmn:CallActivity')) {
        continue;
      }
      this.collectFromElement(context, el);
    }
  }

  private collectFromElement(context: ExpressionVariableProviderContext, el: Element): void {
    const label = elementLabel(el);
    const origin = { originElementId: el.id, originElementLabel: label };

    for (const key of this.listInputKeys(el)) {
      context.addVariable({
        key,
        kind: 'upstreamInput',
        ...origin,
      });
    }

    for (const key of this.listOutputKeys(el)) {
      context.addVariable({
        key,
        kind: 'upstreamOutput',
        ...origin,
      });
    }

    for (const declared of this.listDeclaredOutputKeys(el)) {
      context.addVariable({
        key: declared.key,
        name: declared.name,
        kind: 'declaredOutput',
        ...origin,
      });
    }
  }

  private listInputKeys(el: Element): string[] {
    const keys = new Set<string>();
    for (const p of this.elementModel.getInputParameters(el)) {
      const name = parameterName(p);
      if (name) {
        keys.add(name);
      }
    }
    for (const target of this.elementModel.getCallActivityInTargets(el)) {
      keys.add(target);
    }
    return [...keys];
  }

  private listOutputKeys(el: Element): string[] {
    const keys = new Set<string>();
    for (const p of this.elementModel.getOutputParameters(el)) {
      const name = parameterName(p);
      if (name) {
        keys.add(name);
      }
    }
    for (const source of this.elementModel.getCallActivityOutSources(el)) {
      keys.add(source);
    }
    return [...keys];
  }

  private listDeclaredOutputKeys(el: Element): { key: string; name?: string }[] {
    const comp = this.componentService.getComponentForElement(el);
    if (!comp?.outputParameters?.length) {
      return [];
    }
    const out: { key: string; name?: string }[] = [];
    for (const p of comp.outputParameters) {
      if ((p as { hidden?: boolean }).hidden) {
        continue;
      }
      const key = (p.key ?? '').trim();
      if (key) {
        out.push({ key, name: p.name });
      }
    }
    return out;
  }
}
