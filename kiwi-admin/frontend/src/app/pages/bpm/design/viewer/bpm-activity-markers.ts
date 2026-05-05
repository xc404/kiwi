/**
 * 与画布 `canvas.addMarker(activityId, name)` 一致；样式见 `bpm-viewer.scss`。
 */
export const BPM_ACTIVITY_MARKER_NAMES = [
  'kiwi-bpmn-completed',
  'kiwi-bpmn-active',
  'kiwi-bpmn-error',
] as const;

export type BpmActivityMarkerName = (typeof BPM_ACTIVITY_MARKER_NAMES)[number];

/** 顶部工具栏图例（颜色与画布 stroke 一致） */
export const BPM_ACTIVITY_MARKER_LEGEND: ReadonlyArray<{
  marker: BpmActivityMarkerName;
  label: string;
  variant: 'completed' | 'active' | 'error';
}> = [
  { marker: 'kiwi-bpmn-completed', label: '已完成', variant: 'completed' },
  { marker: 'kiwi-bpmn-active', label: '当前', variant: 'active' },
  { marker: 'kiwi-bpmn-error', label: '异常', variant: 'error' },
];
