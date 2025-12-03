package com.leafy.plant.domain;

import com.leafy.global.common.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Lob;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "nongsaro")
public class NongSaro extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ==========================================
    // 1. 기본 정보
    // ==========================================
    @Column(unique = true)
    private String cntntsNo;        // 컨텐츠 번호
    private String cntntsSj;        // 식물명(국명)
    private String plntbneNm;       // 학명
    private String plntzrNm;        // 영명
    private String distbNm;         // 유통명
    private String fmlNm;           // 과명 (코드)
    private String fmlCodeNm;       // 과명 (코드명)

    @Lob
    @Column(columnDefinition = "TEXT")
    private String orgplceInfo;     // 원산지 정보

    @Lob
    @Column(columnDefinition = "TEXT")
    private String adviseInfo;      // 조언 정보

    private String imageEvlLinkCours; // 이미지 평가 링크 경로

    // ==========================================
    // 2. 성장 및 외형 정보
    // ==========================================
    @Lob
    @Column(columnDefinition = "TEXT")
    private String growthHgInfo;    // 성장 높이 정보

    @Lob
    @Column(columnDefinition = "TEXT")
    private String growthAraInfo;   // 성장 넓이 정보

    @Lob
    @Column(columnDefinition = "TEXT")
    private String lefStleInfo;     // 잎 형태 정보

    private String smellCode;       // 냄새 코드

    @Lob
    @Column(columnDefinition = "TEXT")
    private String smellCodeNm;     // 냄새 코드명

    @Lob
    @Column(columnDefinition = "TEXT")
    private String toxctyInfo;      // 독성 정보

    @Lob
    @Column(columnDefinition = "TEXT")
    private String prpgtEraInfo;    // 번식 시기 정보

    @Lob
    @Column(columnDefinition = "TEXT")
    private String etcEraInfo;      // 기타 시기 정보

    // ==========================================
    // 3. 관리 정보 (Level, Growth, Temp, Winter, Humidity)
    // ==========================================
    private String managelevelCode; // 관리 수준 코드
    private String managelevelCodeNm; // 관리 수준 코드명
    private String grwtveCode;      // 생장 속도 코드
    private String grwtveCodeNm;    // 생장 속도 코드명
    private String grwhTpCode;      // 생육 온도 코드
    private String grwhTpCodeNm;    // 생육 온도 코드명
    private String winterLwetTpCode; // 겨울 최저 온도 코드
    private String winterLwetTpCodeNm; // 겨울 최저 온도 코드명
    private String hdCode;          // 습도 코드
    private String hdCodeNm;        // 습도 코드명

    // ==========================================
    // 4. 토양, 비료, 물주기
    // ==========================================
    @Lob
    @Column(columnDefinition = "TEXT")
    private String frtlzrInfo;      // 비료 정보

    @Lob
    @Column(columnDefinition = "TEXT")
    private String soilInfo;        // 토양 정보

    private String watercycleSprngCode;     // 봄 물주기 코드
    private String watercycleSprngCodeNm;   // 봄 물주기 코드명
    private String watercycleSummerCode;    // 여름 물주기 코드
    private String watercycleSummerCodeNm;  // 여름 물주기 코드명
    private String watercycleAutumnCode;    // 가을 물주기 코드
    private String watercycleAutumnCodeNm;  // 가을 물주기 코드명
    private String watercycleWinterCode;    // 겨울 물주기 코드
    private String watercycleWinterCodeNm;  // 겨울 물주기 코드명

    // ==========================================
    // 5. 병충해 및 관리 상세
    // ==========================================
    @Lob
    @Column(columnDefinition = "TEXT")
    private String dlthtsManageInfo; // 병충해 관리 정보

    @Lob
    @Column(columnDefinition = "TEXT")
    private String speclmanageInfo;  // 특별 관리 정보

    @Lob
    @Column(columnDefinition = "TEXT")
    private String fncltyInfo;       // 기능성 정보

    // ==========================================
    // 6. 규격 정보 (화분, 폭, 높이, 볼륨)
    // ==========================================
    @Lob
    @Column(columnDefinition = "TEXT")
    private String flpodmtBigInfo;   // 화분 직경 대 정보
    @Lob
    @Column(columnDefinition = "TEXT")
    private String flpodmtMddlInfo;  // 화분 직경 중 정보
    @Lob
    @Column(columnDefinition = "TEXT")
    private String flpodmtSmallInfo; // 화분 직경 소 정보
    @Lob
    @Column(columnDefinition = "TEXT")
    private String widthBigInfo;     // 폭 대 정보
    @Lob
    @Column(columnDefinition = "TEXT")
    private String widthMddlInfo;    // 폭 중 정보
    @Lob
    @Column(columnDefinition = "TEXT")
    private String widthSmallInfo;   // 폭 소 정보
    @Lob
    @Column(columnDefinition = "TEXT")
    private String vrticlBigInfo;    // 높이 대 정보
    @Lob
    @Column(columnDefinition = "TEXT")
    private String vrticlMddlInfo;   // 높이 중 정보
    @Lob
    @Column(columnDefinition = "TEXT")
    private String vrticlSmallInfo;  // 높이 소 정보
    @Lob
    @Column(columnDefinition = "TEXT")
    private String volmeBigInfo;     // 볼륨 대 정보
    @Lob
    @Column(columnDefinition = "TEXT")
    private String volmeMddlInfo;    // 볼륨 중 정보
    @Lob
    @Column(columnDefinition = "TEXT")
    private String volmeSmallInfo;   // 볼륨 소 정보
    @Lob
    @Column(columnDefinition = "TEXT")
    private String pcBigInfo;        // 가격 대 정보
    @Lob
    @Column(columnDefinition = "TEXT")
    private String pcMddlInfo;       // 가격 중 정보
    @Lob
    @Column(columnDefinition = "TEXT")
    private String pcSmallInfo;      // 가격 소 정보

    // ==========================================
    // 7. 코드 상세 정보 (분류, 생태, 잎, 꽃, 번식, 광, 배치 등)
    // ==========================================
    private String clCode;           // 분류 코드
    @Lob
    @Column(columnDefinition = "TEXT")
    private String clCodeNm;         // 분류 코드명
    private String grwhstleCode;     // 생육 형태 코드
    @Lob
    @Column(columnDefinition = "TEXT")
    private String grwhstleCodeNm;   // 생육 형태 코드명
    private String indoorpsncpacompositionCode; // 실내 정원 구성 코드
    @Lob
    @Column(columnDefinition = "TEXT")
    private String indoorpsncpacompositionCodeNm; // 실내 정원 구성 코드명
    private String eclgyCode;        // 생태 코드
    @Lob
    @Column(columnDefinition = "TEXT")
    private String eclgyCodeNm;      // 생태 코드명
    private String lefmrkCode;       // 잎 무늬 코드
    @Lob
    @Column(columnDefinition = "TEXT")
    private String lefmrkCodeNm;     // 잎 무늬 코드명
    private String lefcolrCode;      // 잎 색상 코드
    @Lob
    @Column(columnDefinition = "TEXT")
    private String lefcolrCodeNm;    // 잎 색상 코드명
    private String ignSeasonCode;    // 개화 계절 코드
    @Lob
    @Column(columnDefinition = "TEXT")
    private String ignSeasonCodeNm;  // 개화 계절 코드명
    private String flclrCode;        // 꽃 색상 코드
    @Lob
    @Column(columnDefinition = "TEXT")
    private String flclrCodeNm;      // 꽃 색상 코드명
    private String fmldeSeasonCode;   // 열매 계절 코드
    @Lob
    @Column(columnDefinition = "TEXT")
    private String fmldeSeasonCodeNm; // 열매 계절 코드명
    private String fmldecolrCode;     // 열매 색상 코드
    @Lob
    @Column(columnDefinition = "TEXT")
    private String fmldecolrCodeNm;   // 열매 색상 코드명
    private String prpgtmthCode;     // 번식 방법 코드
    @Lob
    @Column(columnDefinition = "TEXT")
    private String prpgtmthCodeNm;   // 번식 방법 코드명
    private String lighttdemanddoCode; // 광 요구도 코드
    @Lob
    @Column(columnDefinition = "TEXT")
    private String lighttdemanddoCodeNm; // 광 요구도 코드명
    private String postngplaceCode;  // 배치 장소 코드
    @Lob
    @Column(columnDefinition = "TEXT")
    private String postngplaceCodeNm; // 배치 장소 코드명
    private String dlthtsCode;       // 병충해 코드
    @Lob
    @Column(columnDefinition = "TEXT")
    private String dlthtsCodeNm;     // 병충해 코드명
    private String managedemanddoCode; // 관리 요구도 코드
    @Lob
    @Column(columnDefinition = "TEXT")
    private String managedemanddoCodeNm; // 관리 요구도 코드명

    // ==========================================
    // 8. 이미지 정보 (원본 변수 + 생성된 URL)
    // ==========================================
    private String rtnFileCours;     // 파일 경로
    private String rtnStreFileNm;    // 저장 파일 명
    private String rtnImageDc;       // 이미지 설명
    private String rtnThumbFileNm;   // 썸네일 파일 명

    @Column(length = 1000)
    private String mainImgUrl;       // 전체 이미지 URL (생성됨)
}