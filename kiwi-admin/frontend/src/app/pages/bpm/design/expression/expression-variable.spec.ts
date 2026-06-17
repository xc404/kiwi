import { expressionVariableKindDetail, spelVariableSuggestionDetail } from '../expression-variable';

describe('expression-variable projectEnv', () => {
  it('expressionVariableKindDetail 返回项目环境变量', () => {
    expect(expressionVariableKindDetail('projectEnv')).toBe('项目环境变量');
  });

  it('spelVariableSuggestionDetail 展示描述', () => {
    expect(
      spelVariableSuggestionDetail({
        key: 'API_URL',
        source: 'projectEnv',
        name: '根地址'
      })
    ).toBe('项目环境变量 · 根地址');
  });
});
