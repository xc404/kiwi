import { Component, inject, OnInit, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import { BaseHttpService } from '@app/core/services/http/base-http.service';
import { PageHeaderComponent } from '@app/shared/components/page-header/page-header.component';
import { NzModalWrapService } from '@app/shared/modal/nz-modal-wrap.service';

import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzCardModule } from 'ng-zorro-antd/card';
import { NzDescriptionsModule } from 'ng-zorro-antd/descriptions';
import { NzListModule } from 'ng-zorro-antd/list';
import { NzMessageService } from 'ng-zorro-antd/message';
import { NzTagModule } from 'ng-zorro-antd/tag';

import { TemplatePackInstallModalComponent } from './template-pack-install-modal.component';

interface TemplatePackDetail {
  pack: {
    id: string;
    name: string;
    summary?: string;
    readme?: string;
    kind?: string;
    category?: string;
    processCount?: number;
    version?: string;
    installCount?: number;
  };
  processes: Array<{ id: string; processKey: string; name: string; entry: boolean }>;
  envKeys: string[];
}

@Component({
  selector: 'app-bpm-market-detail',
  standalone: true,
  imports: [PageHeaderComponent, NzButtonModule, NzCardModule, NzDescriptionsModule, NzListModule, NzTagModule],
  template: `
    <app-page-header></app-page-header>
    <section class="page-content">
      @if (detail()) {
        <nz-card [nzTitle]="detail()!.pack.name">
          <p>{{ detail()!.pack.summary }}</p>
          <nz-descriptions nzBordered nzSize="small" class="m-b-16">
            <nz-descriptions-item nzTitle="类型">{{ detail()!.pack.kind }}</nz-descriptions-item>
            <nz-descriptions-item nzTitle="分类">{{ detail()!.pack.category || '—' }}</nz-descriptions-item>
            <nz-descriptions-item nzTitle="版本">{{ detail()!.pack.version }}</nz-descriptions-item>
            <nz-descriptions-item nzTitle="流程数">{{ detail()!.pack.processCount }}</nz-descriptions-item>
            <nz-descriptions-item nzTitle="安装次数">{{ detail()!.pack.installCount }}</nz-descriptions-item>
          </nz-descriptions>
          @if (detail()!.pack.readme) {
            <pre class="market-readme">{{ detail()!.pack.readme }}</pre>
          }
          <h4>包内流程</h4>
          <nz-list nzBordered [nzDataSource]="detail()!.processes" nzSize="small">
            <ng-template #renderItem let-item>
              <nz-list-item>
                <span>{{ item.name }}</span>
                @if (item.entry) {
                  <nz-tag nzColor="blue">入口</nz-tag>
                }
                <span class="text-muted">{{ item.processKey }}</span>
              </nz-list-item>
            </ng-template>
          </nz-list>
          @if (detail()!.envKeys.length) {
            <h4 class="m-t-16">环境变量</h4>
            <p>{{ detail()!.envKeys.join(', ') }}</p>
          }
          <div class="m-t-16">
            <button nz-button nzType="primary" type="button" (click)="installNewProject()">安装为新项目</button>
            <button class="m-l-8" nz-button type="button" (click)="exportPack()">下载模板包</button>
            <button class="m-l-8" nz-button type="button" (click)="goBack()">返回列表</button>
          </div>
        </nz-card>
      }
    </section>
  `,
  styles: [
    `
      .market-readme {
        white-space: pre-wrap;
        background: #fafafa;
        padding: 12px;
        border-radius: 4px;
      }
      .text-muted {
        color: rgba(0, 0, 0, 0.45);
        margin-left: 8px;
      }
    `
  ]
})
export class BpmMarketDetail implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly http = inject(BaseHttpService);
  private readonly modalWrap = inject(NzModalWrapService);
  private readonly message = inject(NzMessageService);

  readonly detail = signal<TemplatePackDetail | null>(null);
  private packId = '';

  ngOnInit(): void {
    this.packId = this.route.snapshot.paramMap.get('packId') ?? '';
    if (this.packId) {
      this.load();
    }
  }

  load(): void {
    this.http.get<TemplatePackDetail>(`/bpm/market/${this.packId}`).subscribe(d => this.detail.set(d));
  }

  installNewProject(): void {
    const d = this.detail();
    if (!d) {
      return;
    }
    const ref = this.modalWrap.create({
      nzTitle: '安装模板包',
      nzContent: TemplatePackInstallModalComponent,
      nzData: { packName: d.pack.name, mode: 'newProject' as const },
      nzOnOk: () => {
        const comp = ref.getContentComponent() as TemplatePackInstallModalComponent;
        const body = comp.tryGetPayload();
        if (!body) {
          return false;
        }
        return firstValueFrom(
          this.http.post<{ projectId: string }>(`/bpm/market/${this.packId}/install`, body, { needSuccessInfo: true })
        ).then(res => {
          void this.router.navigate(['/bpm/process-definition'], { queryParams: { projectId: res.projectId } });
        });
      }
    });
  }

  exportPack(): void {
    const d = this.detail();
    if (!d || !this.packId) {
      return;
    }
    const version = d.pack.version?.trim() || '1.0.0';
    const filename = `${d.pack.name.trim() || 'template-pack'}-${version}.kiwi-template-pack`;
    this.http.downloadGet(`/bpm/market/${this.packId}/export`, filename);
  }

  goBack(): void {
    void this.router.navigate(['/bpm/market']);
  }
}
