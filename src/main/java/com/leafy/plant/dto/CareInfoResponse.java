package com.leafy.plant.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CareInfoResponse {
    private int waterCycle;
    private int fertilizerCycle;
    private int repotCycle;
}
