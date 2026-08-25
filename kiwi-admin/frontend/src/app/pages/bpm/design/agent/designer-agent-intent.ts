/** 对话框输入 → 人机闸门意图（轻量规则，按钮仍作兜底） */

const ApprovePattern = /^(确认|批准|执行|保存|可以|好的|同意|ok|yes)(吧|了|执行|保存)?[.!。！]?$/i;

/** 短句且为明确批准语时视为 confirmed=true */
export function isApproveIntent(text: string): boolean {
  const trimmed = text.trim();
  if (!trimmed || trimmed.length > 32) {
    return false;
  }
  return ApprovePattern.test(trimmed);
}

export function planFeedbackFromText(text: string): { confirmed: boolean; feedbackText?: string } {
  if (isApproveIntent(text)) {
    return { confirmed: true };
  }
  return { confirmed: false, feedbackText: text.trim() };
}

export function previewFeedbackFromText(text: string): { confirmed: boolean; feedbackText?: string } {
  if (isApproveIntent(text)) {
    return { confirmed: true };
  }
  return { confirmed: false, feedbackText: text.trim() };
}
