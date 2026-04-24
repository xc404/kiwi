import { BreakpointObserver } from '@angular/cdk/layout';
import { NgClass } from '@angular/common';
import { Component, OnInit, ChangeDetectionStrategy, ChangeDetectorRef, inject, DestroyRef, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, FormGroup, Validators, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { take } from 'rxjs';
import { finalize } from 'rxjs/operators';

import { ValidatorsService } from '@core/services/validators/validators.service';
import { AccountService } from '@services/system/account.service';
import { fnCheckForm } from '@utils/tools';

import { NzAvatarModule } from 'ng-zorro-antd/avatar';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzCardModule } from 'ng-zorro-antd/card';
import { NzWaveModule } from 'ng-zorro-antd/core/wave';
import { NzFormModule } from 'ng-zorro-antd/form';
import { NzGridModule } from 'ng-zorro-antd/grid';
import { NzIconModule } from 'ng-zorro-antd/icon';
import { NzInputModule } from 'ng-zorro-antd/input';
import { NzMessageService } from 'ng-zorro-antd/message';
import { NzSelectModule } from 'ng-zorro-antd/select';
import { NzUploadChangeParam, NzUploadModule } from 'ng-zorro-antd/upload';

@Component({
  selector: 'app-base',
  templateUrl: './base.component.html',
  styleUrls: ['./base.component.less'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    NzGridModule,
    FormsModule,
    NzFormModule,
    ReactiveFormsModule,
    NzInputModule,
    NzSelectModule,
    NzButtonModule,
    NzWaveModule,
    NgClass,
    NzAvatarModule,
    NzUploadModule,
    NzIconModule,
    NzCardModule
  ]
})
export class BaseComponent implements OnInit {
  readonly data = input.required<{
    label: string;
  }>();
  validateForm!: FormGroup;
  selectedProvince = 'Zhejiang';
  selectedCity = 'Hangzhou';
  provinceData = ['Zhejiang', 'Jiangsu'];
  formOrder = 1;
  avatarOrder = 0;
  cityData: Record<string, string[]> = {
    Zhejiang: ['Hangzhou', 'Ningbo', 'Wenzhou'],
    Jiangsu: ['Nanjing', 'Suzhou', 'Zhenjiang']
  };
  destroyRef = inject(DestroyRef);

  private fb = inject(FormBuilder);
  private msg = inject(NzMessageService);
  private validatorsService = inject(ValidatorsService);
  private breakpointObserver = inject(BreakpointObserver);
  private cdr = inject(ChangeDetectorRef);
  private accountService = inject(AccountService);

  /** cryoEMS 等机机调 Kiwi 用的 Authorization 完整值 */
  readonly issuedAuthorization = signal<string | null>(null);
  readonly issuingIntegrationToken = signal(false);

  provinceChange(value: string): void {
    this.selectedCity = this.cityData[value][0];
    this.selectedProvince = value;
    this.validateForm.get('city')?.setValue(this.selectedCity);
  }

  initForm(): void {
    this.validateForm = this.fb.group({
      email: [null, [Validators.required]],
      area: [null, [Validators.required]],
      nickName: [null],
      desc: [null, [Validators.required]],
      city: [null, [Validators.required]],
      province: [null, [Validators.required]],
      mobile: [null, [Validators.required, this.validatorsService.mobileValidator()]],
      telephone: [null, [Validators.required, this.validatorsService.telephoneValidator()]],
      street: [null, [Validators.required]]
    });
  }

  submitForm(): void {
    if (!fnCheckForm(this.validateForm)) {
      return;
    }
  }

  handleChange(info: NzUploadChangeParam): void {
    if (info.file.status !== 'uploading') {
      console.log(info.file, info.fileList);
    }
    if (info.file.status === 'done') {
      this.msg.success(`${info.file.name} file uploaded successfully`);
    } else if (info.file.status === 'error') {
      this.msg.error(`${info.file.name} file upload failed.`);
    }
  }

  obBreakPoint(): void {
    this.breakpointObserver
      .observe(['(max-width: 1200px)'])
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(result => {
        if (result.matches) {
          this.formOrder = 1;
          this.avatarOrder = 0;
        } else {
          this.formOrder = 0;
          this.avatarOrder = 1;
        }
        this.cdr.markForCheck();
      });
  }

  ngOnInit(): void {
    this.initForm();
    this.obBreakPoint();
  }

  issueIntegrationToken(): void {
    this.issuingIntegrationToken.set(true);
    this.accountService
      .issueIntegrationApiToken()
      .pipe(
        take(1),
        finalize(() => {
          this.issuingIntegrationToken.set(false);
          this.cdr.markForCheck();
        })
      )
      .subscribe({
        next: res => {
          const prefix = (res.tokenType || 'Bearer').trim();
          this.issuedAuthorization.set(`${prefix} ${res.token}`.replace(/\s+/g, ' ').trim());
          this.msg.success('签发成功，请立即复制保存；再次签发会使旧令牌失效');
        },
        error: () => {
          /* BaseHttpService 已弹出错误 */
        }
      });
  }

  copyAuthorization(value: string): void {
    void navigator.clipboard.writeText(value).then(
      () => this.msg.success('已复制'),
      () => this.msg.error('复制失败，请手动选择文本复制')
    );
  }
}
