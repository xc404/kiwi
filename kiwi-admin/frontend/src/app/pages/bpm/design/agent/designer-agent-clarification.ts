export interface ClarificationOption {
  id: string;
  label: string;
}

export interface ClarificationQuestion {
  id: string;
  prompt: string;
  options: ClarificationOption[];
  allowMultiple?: boolean;
}

export interface ClarificationForm {
  questions: ClarificationQuestion[];
}

export interface DesignerAgentHitlItem {
  id: string;
  kind: string;
  title: string;
  detail?: string;
  payloadJson?: string;
}

export function parseClarificationForm(json?: string | null): ClarificationForm | null {
  if (!json?.trim()) {
    return null;
  }
  try {
    return JSON.parse(json) as ClarificationForm;
  } catch {
    return null;
  }
}

export function parseHitlItems(json?: string | null): DesignerAgentHitlItem[] {
  if (!json?.trim()) {
    return [];
  }
  try {
    const parsed = JSON.parse(json) as DesignerAgentHitlItem[];
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}
