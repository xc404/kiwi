import type { BpmDesignerToolbarCommand, BpmDesignerToolbarGroup } from './bpm-designer-toolbar.types';

export type ToolbarButtonConfig = {
  tooltip: string;
  icon: string;
  onClick: () => void;
};

export type ToolbarSegment =
  | { type: 'divider' }
  | { type: 'group'; buttons: ToolbarButtonConfig[] };

const GROUP_ORDER: BpmDesignerToolbarGroup[] = ['tools', 'edit', 'view', 'file'];

export function buildToolbarSegments(
  commands: readonly BpmDesignerToolbarCommand[],
  onCommand: (id: string) => void,
): ToolbarSegment[] {
  const segments: ToolbarSegment[] = [];
  let lastGroup: BpmDesignerToolbarGroup | null = null;

  for (const group of GROUP_ORDER) {
    const inGroup = commands.filter((c) => c.group === group && c.showInToolbar !== false);
    if (inGroup.length === 0) {
      continue;
    }
    if (lastGroup !== null) {
      segments.push({ type: 'divider' });
    }
    segments.push({
      type: 'group',
      buttons: inGroup.map((c) => ({
        tooltip: c.tooltip,
        icon: c.icon,
        onClick: () => onCommand(c.id),
      })),
    });
    lastGroup = group;
  }

  return segments;
}
