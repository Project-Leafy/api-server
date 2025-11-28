package com.leafy.recommendation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecommendationResponseDto {

    // 상세 페이지 이동을 위한 ID
    private Long speciesId;

    // 한글 이름 (예: 몬스테라)
    private String koreanName;

    // 학명 (필요 시 표시)
    private String scientificName;

    // 식물 사진 URL (Card UI에 표시)
    private String officialImageUrl;

    // 추천 이유 태그 목록 (예: ["#초보자용", "#반려동물안전", "#물주기쉬움"])
    private List<String> tags;

    // (선택 사항) 추천 적합도 점수 (정렬용)
    private int matchScore;
}