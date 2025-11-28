package com.leafy.plant.dto;

import java.time.LocalDate;

public record CreateMyPlantRequest(
        Long speciesId,
        String nickname,
        String imageUrl,
        LocalDate adoptionDate
) {}