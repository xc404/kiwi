package com.kiwi.project.bpm.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
@Schema(description = "远程市场条目详情（含 manifest）")
public class BpmRemoteMarketItemDetailDto extends BpmRemoteMarketItemDto {

    @Schema(description = "manifest.json 解析结果")
    private Map<String, Object> manifest;
}
