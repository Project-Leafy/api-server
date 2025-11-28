package com.leafy.journal.dto;

import java.time.LocalDate;

public record UpdateGrowthRecordRequest(
    LocalDate recordDate,
    String memo,
    Boolean watered,
    Boolean fertilized,
    Boolean pruned,
    Boolean repotted,
    String waterAmountType,
    String fertilizerType
) {}
