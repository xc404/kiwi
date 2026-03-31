## 1. Layout alignment

- [x] 1.1 Refactor `bpm-viewer.html` / `bpm-viewer.scss` to use `nz-layout` + right `nz-sider` (width and scroll wrapper aligned with `bpm-editor` properties column, e.g. `bpm-editor-properties-sider` / `bpm-editor-properties-scroll` patterns).
- [x] 1.2 Adjust main/canvas column flex rules so diagram + loading state behave correctly next to the new sidebar.

## 2. Reuse bpm-properties-panel on instance viewer

- [x] 2.1 Extend `BpmPropertiesPanel` (and/or add a thin adapter) so it can run against `NavigatedViewer` in read-only mode: subscribe to `selection.changed`, drive `bpm-panel-header`, and guard or hide modeling-only `PropertyGroup` paths.
- [x] 2.2 Integrate runtime variable display into the panel for instance mode (reuse `filteredVariables` / `selectionIsRoot` semantics from `BpmViewer`—either as a dedicated tab or injected PropertyTab), preserving formatting behavior previously in `bpm-instance-properties`.
- [x] 2.3 Replace `bpm-panel-header` + `bpm-instance-properties` usage in `bpm-viewer` with `bpm-properties-panel` and update `bpm-viewer.ts` imports.

## 3. Cleanup and verification

- [x] 3.1 Remove or keep `bpm-instance-properties` only if still referenced elsewhere; delete dead files if unused.
- [ ] 3.2 Manual test: open an instance, switch root vs activity selection, confirm variable filtering and UI match previous behavior; confirm no console errors from missing `modeling` on viewer.
