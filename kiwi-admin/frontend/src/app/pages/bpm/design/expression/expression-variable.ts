/** 表达式编辑器可用变量来源（设计时上游 + 项目环境变量） */
export type ExpressionVariableKind = 'upstreamInput' | 'upstreamOutput' | 'declaredOutput' | 'projectEnv';

export interface ExpressionVariable {
  key: string;
  name?: string;
  kind: ExpressionVariableKind;
  originElementId?: string;
  originElementLabel?: string;
}

export interface ExpressionMethod {
  label: string;
  insert: string;
}

/** 供 SpEL/JUEL 编辑器与 Formly 使用的补全项 */
export type SpelVariableSuggestionSource = ExpressionVariableKind | 'referenced';

export interface SpelVariableSuggestion {
  key: string;
  name?: string;
  source: SpelVariableSuggestionSource;
  originElementLabel?: string;
}

export function spelVariableSuggestionDetail(v: SpelVariableSuggestion): string {
  const kind = expressionVariableKindDetail(v.source);
  if (v.source === 'projectEnv' && v.name?.trim()) {
    return `${kind} · ${v.name.trim()}`;
  }
  return v.originElementLabel ? `${kind} · ${v.originElementLabel}` : kind;
}

export function expressionVariableKindDetail(kind: ExpressionVariableKind | SpelVariableSuggestionSource): string {
  switch (kind) {
    case 'upstreamInput':
      return '上游输入';
    case 'upstreamOutput':
      return '上游输出';
    case 'declaredOutput':
      return '声明输出';
    case 'projectEnv':
      return '项目环境变量';
    case 'referenced':
      return '运行时变量';
    default:
      return '';
  }
}

export function toSpelVariableSuggestions(variables: ExpressionVariable[]): SpelVariableSuggestion[] {
  return [...variables]
    .sort((a, b) => a.key.localeCompare(b.key))
    .map(v => ({
      key: v.key,
      name: v.name,
      source: v.kind,
      originElementLabel: v.originElementLabel
    }));
}
