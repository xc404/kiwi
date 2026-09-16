import type { BpmDesignerToolbarCommand, BpmDesignerToolbarGroup } from './bpm-designer-toolbar.types';

export interface ToolbarButtonConfig {
  id: string;
  tooltip: string;
  icon: string;
  label: string;
  onClick: () => void;
}

export type ToolbarSegment = { type: 'divider' } | { type: 'group'; buttons: ToolbarButtonConfig[] };

export interface ToolbarOverflowGroup {
  group: BpmDesignerToolbarGroup;
  title: string;
  items: ToolbarButtonConfig[];
}

export interface ToolbarLayout {
  segments: ToolbarSegment[];
  overflowGroups: ToolbarOverflowGroup[];
}

const GROUP_ORDER: BpmDesignerToolbarGroup[] = ['tools', 'edit', 'view', 'file'];

const OVERFLOW_GROUP_TITLE: Record<BpmDesignerToolbarGroup, string> = {
  tools: '画布工具',
  edit: '编辑',
  view: '视图',
  file: '文件'
};

function visibleCommands(commands: readonly BpmDesignerToolbarCommand[]): BpmDesignerToolbarCommand[] {
  return commands.filter(c => c.showInToolbar !== false);
}

function toButton(c: BpmDesignerToolbarCommand, onCommand: (id: string) => void): ToolbarButtonConfig {
  return {
    id: c.id,
    tooltip: c.tooltip,
    icon: c.icon,
    label: c.menuLabel ?? c.tooltip,
    onClick: () => onCommand(c.id)
  };
}

export function buildToolbarLayout(commands: readonly BpmDesignerToolbarCommand[], onCommand: (id: string) => void): ToolbarLayout {
  const visible = visibleCommands(commands);
  const iconCommands = visible.filter(c => !c.overflow);
  const overflowCommands = visible.filter(c => c.overflow);

  const segments: ToolbarSegment[] = [];
  let lastGroup: BpmDesignerToolbarGroup | null = null;

  for (const group of GROUP_ORDER) {
    const inGroup = iconCommands.filter(c => c.group === group);
    if (inGroup.length === 0) {
      continue;
    }
    if (lastGroup !== null) {
      segments.push({ type: 'divider' });
    }
    segments.push({
      type: 'group',
      buttons: inGroup.map(c => toButton(c, onCommand))
    });
    lastGroup = group;
  }

  const overflowGroups: ToolbarOverflowGroup[] = [];
  for (const group of GROUP_ORDER) {
    const inGroup = overflowCommands.filter(c => c.group === group);
    if (inGroup.length === 0) {
      continue;
    }
    overflowGroups.push({
      group,
      title: OVERFLOW_GROUP_TITLE[group],
      items: inGroup.map(c => toButton(c, onCommand))
    });
  }

  return { segments, overflowGroups };
}
