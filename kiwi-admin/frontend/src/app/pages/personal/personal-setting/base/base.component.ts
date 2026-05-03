import { BreakpointObserver } from '@angular/cdk/layout';
import { NgClass } from '@angular/common';
import { Component, OnInit, ChangeDetectionStrategy, ChangeDetectorRef, inject, DestroyRef, input } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, FormGroup, Validators, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { finalize } from 'rxjs/operators';

import { SessionService } from '@core/services/common/session.service';
import { AccountService } from '@core/services/http/system/account.service';
import { LoginService } from '@core/services/http/login/login.service';
import { ValidatorsService } from '@core/services/validators/validators.service';
import { UserInfoStoreService } from '@store/common-store/userInfo-store.service';
import { fnCheckForm } from '@utils/tools';
import { environment } from '@env/environment';

import { NzAvatarModule } from 'ng-zorro-antd/avatar';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { NzWaveModule } from 'ng-zorro-antd/core/wave';
import { NzFormModule } from 'ng-zorro-antd/form';
import { NzGridModule } from 'ng-zorro-antd/grid';
import { NzInputModule } from 'ng-zorro-antd/input';
import { NzMessageService } from 'ng-zorro-antd/message';
import { NzSelectModule } from 'ng-zorro-antd/select';

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
    NzAvatarModule
  ]
})
export class BaseComponent implements OnInit {
  readonly data = input.required<{
    label: string;
  }>();
  validateForm!: FormGroup;
  formOrder = 1;
  avatarOrder = 0;
  submitting = false;
  readonly sexOptions = [
    { label: '男', value: '0' },
    { label: '女', value: '1' },
    { label: '未知', value: '2' }
  ];

  destroyRef = inject(DestroyRef);
  private fb = inject(FormBuilder);
  private msg = inject(NzMessageService);
  private validatorsService = inject(ValidatorsService);
  private breakpointObserver = inject(BreakpointObserver);
  private cdr = inject(ChangeDetectorRef);
  private accountService = inject(AccountService);
  private loginService = inject(LoginService);
  private sessionService = inject(SessionService);
  private userInfoStore = inject(UserInfoStoreService);

  initForm(): void {
    this.validateForm = this.fb.group({
      userName: [{ value: '', disabled: true }],
      nickName: [''],
      email: ['', [Validators.required, this.validatorsService.emailValidator()!]],
      phonenumber: ['', [this.validatorsService.mobileValidator()!]],
      sex: ['2'],
      avatar: ['']
    });
  }

  /** 与顶部栏一致：相对路径拼 API 根 */
  avatarPreviewUrl(): string {
    const raw = (this.validateForm?.get('avatar')?.value as string | null | undefined)?.trim();
    if (!raw) {
      return 'imgs/default_face.png';
    }
    if (raw.startsWith('http://') || raw.startsWith('https://') || raw.startsWith('data:')) {
      return raw;
    }
    const base = environment.api.baseUrl.replace(/\/$/, '');
    const path = raw.startsWith('/') ? raw : `/${raw}`;
    return `${base}${path}`;
  }

  private patchFormFromSessionUser(u: {
    userName?: string;
    nickName?: string;
    email?: string;
    phonenumber?: string;
    sex?: string;
    avatar?: string;
  }): void {
    this.validateForm.patchValue({
      userName: u.userName ?? '',
      nickName: u.nickName ?? '',
      email: u.email ?? '',
      phonenumber: u.phonenumber ?? '',
      sex: u.sex != null && u.sex !== '' ? u.sex : '2',
      avatar: u.avatar ?? ''
    });
    this.cdr.markForCheck();
  }

  loadProfile(): void {
    this.loginService
      .getUserInfo()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(info => {
        this.patchFormFromSessionUser(info);
      });
  }

  submitForm(): void {
    if (!fnCheckForm(this.validateForm)) {
      return;
    }
    const v = this.validateForm.getRawValue() as {
      nickName: string;
      email: string;
      phonenumber: string;
      sex: string;
      avatar: string;
    };
    this.submitting = true;
    this.cdr.markForCheck();
    this.accountService
      .editAccount({
        nickName: v.nickName?.trim() || null,
        email: v.email?.trim() || null,
        phonenumber: v.phonenumber?.trim() || null,
        sex: v.sex || null,
        avatar: v.avatar?.trim() || null
      })
      .pipe(
        finalize(() => {
          this.submitting = false;
          this.cdr.markForCheck();
        }),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe(() => {
        void this.sessionService.refreshSession().then(() => {
          const refreshed = this.userInfoStore.$userInfo();
          this.patchFormFromSessionUser(refreshed);
        });
      });
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
    this.patchFormFromSessionUser(this.userInfoStore.$userInfo());
    this.loadProfile();
    this.obBreakPoint();
  }
}
