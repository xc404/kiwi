import { Component, inject } from '@angular/core';

import { NZ_MODAL_DATA } from 'ng-zorro-antd/modal';

import { BpmProjectEnv } from './bpm-project-env';

export interface BpmProjectEnvModalData {
  projectId: string;
}

/** 在弹窗中编辑项目环境变量 */
@Component({
  selector: 'bpm-project-env-modal',
  standalone: true,
  imports: [BpmProjectEnv],
  template: `<app-bpm-project-env [projectId]="nzData.projectId" />`
})
export class BpmProjectEnvModalComponent {
  readonly nzData = inject<BpmProjectEnvModalData>(NZ_MODAL_DATA);
}
