import { Component } from '@angular/core';

import { PageHeaderComponent } from '@app/shared/components/page-header/page-header.component';

import { BpmComponentPlugins } from './bpm-component-plugins';

@Component({
  selector: 'app-bpm-component-plugin',
  standalone: true,
  imports: [PageHeaderComponent, BpmComponentPlugins],
  template: `
    <app-page-header></app-page-header>
    <section class="page-content">
      <app-bpm-component-plugins />
    </section>
  `
})
export class BpmComponentPlugin {}
