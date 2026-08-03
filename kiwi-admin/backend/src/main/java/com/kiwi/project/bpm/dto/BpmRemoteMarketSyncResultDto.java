package com.kiwi.project.bpm.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "远程市场同步结果")
public class BpmRemoteMarketSyncResultDto {

    private int sourceCount;
    private int itemCount;
    private long fetchedAt;
}
