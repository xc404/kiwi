import { EnvironmentProviders, InjectionToken, makeEnvironmentProviders } from '@angular/core';

import { ExpressionVariableProvider } from './expression-variable-provider';
import { BpmnModelerUpstreamVariableProvider } from './providers/bpmn-modeler-upstream-variable-provider';
import { BpmProjectEnvVariableProvider } from './providers/bpm-project-env-variable-provider';

/**
 * 多提供者 token：按注册顺序合并各 `ExpressionVariableProvider` 贡献的变量。
 */
export const EXPRESSION_VARIABLE_PROVIDER_CONTRIBUTOR = new InjectionToken<ExpressionVariableProvider>(
  'ExpressionVariableProviderContributor'
);

/** 注册 BPM 表达式变量默认贡献者：上游节点变量 → 项目环境变量 */
export function provideBpmDefaultExpressionVariableProviders(): EnvironmentProviders {
  return makeEnvironmentProviders([
    { provide: EXPRESSION_VARIABLE_PROVIDER_CONTRIBUTOR, useExisting: BpmnModelerUpstreamVariableProvider, multi: true },
    { provide: EXPRESSION_VARIABLE_PROVIDER_CONTRIBUTOR, useExisting: BpmProjectEnvVariableProvider, multi: true }
  ]);
}
