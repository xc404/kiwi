import { Component } from '@angular/core';

import { ProcessSelector } from '@app/pages/bpm/flow-elements/process-selector';
import { FieldType, FieldTypeConfig } from '@ngx-formly/core';

import { NzInputModule } from 'ng-zorro-antd/input';

@Component({
  selector: 'process-selector-type',
  template: ` <bpm-process-selector [control]="formControl" [excludeProcessId]="props['excludeProcessId']" [projectId]="props['projectId']"></bpm-process-selector> `,
  imports: [NzInputModule, ProcessSelector]
})
export class ProcessSelectorType extends FieldType<FieldTypeConfig> {}
