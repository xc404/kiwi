/** 前端无法唯一确定追加锚点时抛出，供 AI 对话引导用户选择元素 */
export class BpmAppendAnchorRequiredError extends Error {
  readonly componentName: string;
  readonly candidateElementIds: readonly string[];

  constructor(componentName: string, candidateElementIds: string[]) {
    const list = candidateElementIds.length ? candidateElementIds.join('、') : '（无）';
    super(
      `已匹配组件「${componentName}」，但无法确定追加到哪个节点。请在画布上选中一个节点后重试，或在对话中说明元素 id（例如：${list}）。`,
    );
    this.name = 'BpmAppendAnchorRequiredError';
    this.componentName = componentName;
    this.candidateElementIds = candidateElementIds;
  }
}
