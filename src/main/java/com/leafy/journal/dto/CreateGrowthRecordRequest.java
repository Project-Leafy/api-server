package com.leafy.journal.dto;

import java.time.LocalDate;

public record CreateGrowthRecordRequest(
    LocalDate recordDate,
    String photoUrl,
    String memo,
    Boolean watered,
    Boolean fertilized,
    Boolean pruned,
    Boolean repotted,
    String waterAmountType,
    String fertilizerType
) {}
