import { Component, input } from "@angular/core";
import { NzCollapseComponent, NzCollapsePanelComponent } from "ng-zorro-antd/collapse";


@Component({
    selector: 'app-bpm-component-edit-panel',
    template: `<nz-collapse>
            <nz-collapse-panel nzHeader="组件配置" [nzActive]="true">
                <p>属性内容</p>
            </nz-collapse-panel>
              <nz-collapse-panel nzHeader="输入" [nzActive]="true">
                <p>属性内容</p>
            </nz-collapse-panel>
              <nz-collapse-panel nzHeader="输出" [nzActive]="true">
                <p>属性内容</p>
            </nz-collapse-panel>
    </nz-collapse>`,
    imports: [NzCollapseComponent, NzCollapsePanelComponent]
})
export class BpmComponentEditPanel {

    component = input.required<any>();

}