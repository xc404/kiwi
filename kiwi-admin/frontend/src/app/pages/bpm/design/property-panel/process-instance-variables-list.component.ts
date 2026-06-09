import { CommonModule } from '@angular/common';
import { Component, input } from '@angular/core';

import { CamundaHistoricVariableInstance } from '../service/process-instance.service';
import { ReadonlyPropertyRowComponent } from './readonly-property-row/readonly-property-row.component';
import { PropertyDescription } from './types';

@Component({
  selector: 'bpm-process-instance-variables-list',
  templateUrl: './process-instance-variables-list.component.html',
  styleUrls: ['./process-instance-variables-list.component.css'],
  imports: [CommonModule, ReadonlyPropertyRowComponent],
  standalone: true
})
export class ProcessInstanceVariablesListComponent {
  processInstanceVariables = input.required<CamundaHistoricVariableInstance[]>();

  trackProcessVariable(index: number, v: CamundaHistoricVariableInstance): string {
    return `${v.name ?? ''}-${index}`;
  }

  processVariablePropertyDescription(v: CamundaHistoricVariableInstance): PropertyDescription {
    const name = typeof v.name === 'string' && v.name.length > 0 ? v.name : '';
    const typeStr = v.type != null && String(v.type).length > 0 ? String(v.type) : '';
    return {
      key: name,
      name: name || '—',
      description: typeStr ? `类型: ${typeStr}` : undefined,
      readonly: true
    };
  }
}
