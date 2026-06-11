import { EnvironmentProviders, inject, Injectable, InjectionToken, makeEnvironmentProviders } from '@angular/core';

import { Element } from 'bpmn-js/lib/model/Types';

import { BasePropertyProvider } from './base-property-provider';
import { PropertyProvider, PropertyTab } from './types';
import { ComponentPropertyProvider } from '../../flow-elements/component-property-provider';

export type { PropertyTab, PropertyProvider } from './types';

/**
 * 多提供者 token：按注册顺序依次合并各 `PropertyProvider` 的 Tab。
 * 应用须至少注册默认贡献者（见 `provideBpmDefaultPropertyProviderContributors`）。
 */
export const PROPERTY_PROVIDER_CONTRIBUTOR = new InjectionToken<PropertyProvider>('PropertyProviderContributor');

/** 将 `extraTabs` 按 Tab 名合并进 `merged`（同名 Tab 的 groups 追加）。 */
export function mergePropertyTabLists(merged: PropertyTab[], extraTabs: PropertyTab[]): PropertyTab[] {
  if (extraTabs.length === 0) {
    return merged.map(t => ({
      ...t,
      groups: [...t.groups]
    }));
  }
  const result = merged.map(t => ({
    ...t,
    groups: [...t.groups]
  }));
  for (const extra of extraTabs) {
    const idx = result.findIndex(t => (t.name ?? '') === (extra.name ?? ''));
    if (idx >= 0) {
      const cur = result[idx];
      result[idx] = {
        ...cur,
        groups: [...cur.groups, ...extra.groups]
      };
    } else {
      result.push({
        ...extra,
        groups: [...extra.groups]
      });
    }
  }
  return result;
}

@Injectable({ providedIn: 'root' })
export class CompositePropertyProvider {
  /** multi provider 在运行时注入为数组；Angular 类型定义侧仍为单项类型 */
  private readonly contributors = inject(PROPERTY_PROVIDER_CONTRIBUTOR) as unknown as PropertyProvider[];

  getProperties(element: Element): PropertyTab[] {
    let merged: PropertyTab[] = [];
    for (const p of this.contributors) {
      merged = mergePropertyTabLists(merged, p.getProperties(element));
    }
    return merged;
  }
}

/** 注册 BPM 属性面板默认贡献者：基础通用属性 → 组件输入/输出（顺序与合并语义一致）。 */
export function provideBpmDefaultPropertyProviderContributors(): EnvironmentProviders {
  return makeEnvironmentProviders([
    { provide: PROPERTY_PROVIDER_CONTRIBUTOR, useExisting: BasePropertyProvider, multi: true },
    { provide: PROPERTY_PROVIDER_CONTRIBUTOR, useExisting: ComponentPropertyProvider, multi: true }
  ]);
}
