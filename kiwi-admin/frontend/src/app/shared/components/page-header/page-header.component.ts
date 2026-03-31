import { ChangeDetectionStrategy, Component, inject, TemplateRef, input, OnInit, signal, WritableSignal } from '@angular/core';
import { Router } from '@angular/router';
import { MenuStoreService } from '@app/core/services/store/common-store/menu-store.service';
import { Menu } from '@app/core/services/types';

import { NzBreadCrumbModule } from 'ng-zorro-antd/breadcrumb';
import { NzOutletModule } from 'ng-zorro-antd/core/outlet';
import { NzSafeAny } from 'ng-zorro-antd/core/types';
import { NzPageHeaderModule } from 'ng-zorro-antd/page-header';

export interface PageHeaderType {
  title: string;
  desc: string | TemplateRef<NzSafeAny>;
  extra: string | TemplateRef<NzSafeAny>;
  breadcrumb: string[];
  footer: string | TemplateRef<NzSafeAny>;
}

@Component({
  selector: 'app-page-header',
  templateUrl: './page-header.component.html',
  styleUrls: ['./page-header.component.less'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [NzPageHeaderModule, NzBreadCrumbModule, NzOutletModule]
})
export class PageHeaderComponent implements OnInit {
  private router = inject(Router);
  private menuStoreService = inject(MenuStoreService);
  readonly backTpl = input<TemplateRef<NzSafeAny>>();
  readonly pageHeaderInfo = input<Partial<PageHeaderType>>({});
  readonly backUrl = input('');

  breadcrumb: WritableSignal<string[]> = signal([]);

  back(): void {
    this.router.navigateByUrl(this.backUrl());
  }


  ngOnInit(): void {

    this.menuStoreService.getMenuArrayStore().subscribe(menu => {
      let url = this.router.routerState.snapshot.url;
      let breadcrumb: Menu[] = [];
      this.findMenu(menu, url, breadcrumb);
      this.breadcrumb.set(breadcrumb.map(item => item.name));
    });
  }

  findMenu(menu: Menu[], url: string, breadcrumb: Menu[]): boolean {
    for (let i = 0; i < menu.length; i++) {
      breadcrumb.push(menu[i]);
      if (menu[i].path === url) {
        return true;
      }
      if (menu[i].children) {
        if (this.findMenu(menu[i].children as Menu[], url, breadcrumb)) {
          return true;
        }
      }
      breadcrumb.pop();
    }
    return false;
  }

}
