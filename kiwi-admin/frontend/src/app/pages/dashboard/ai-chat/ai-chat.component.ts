import { ChangeDetectionStrategy, Component } from '@angular/core';

import { ChatComponent } from '@shared/components/chat/chat.component';
import { NzBreadCrumbModule } from 'ng-zorro-antd/breadcrumb';
import { NzCardModule } from 'ng-zorro-antd/card';

@Component({
  selector: 'app-ai-chat',
  templateUrl: './ai-chat.component.html',
  styleUrls: ['./ai-chat.component.less'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [NzBreadCrumbModule, NzCardModule, ChatComponent]
})
export class AiChatComponent {}
