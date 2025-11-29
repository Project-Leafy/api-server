// com/leafy/plant/domain/MyPlant.java

package com.leafy.plant.domain;


import com.leafy.global.common.BaseTimeEntity;
import com.leafy.user.domain.User; // 1. 다른 도메인의 User 엔티티 import
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Entity
@Table(name = "my_plant")
public class MyPlant extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "plant_id")
    private Long plantId;

    // 2. N:1 관계 (MyPlant(N) -> User(1))
    @ManyToOne(fetch = FetchType.LAZY) // 3. 지연 로딩(LAZY)으로 설정
    @JoinColumn(name = "user_id", nullable = false) // 4. DB의 'user_id' 컬럼과 매핑
    private User user; // 5. 'user_id'가 아닌 'User' 객체 자체를 참조

    // 6. N:1 관계 (MyPlant(N) -> PlantSpecies(1))
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "species_id") // 7. species_id 컬럼과 매핑 (ERD상 nullable)
    private PlantSpecies plantSpecies;

    @Column(nullable = false, length = 100)
    private String nickname;

    @Column(name = "adoption_date", nullable = false)
    private LocalDate adoptionDate;

    @Column(name = "image_url", length = 2048)
    private String imageUrl;

    private LocalDate lastWateredDate;

    @Builder.Default
    @Column(name = "status_code", nullable = false, length = 50)
    private String statusCode = "HEALTHY"; // (Tip: Enum 관리 추천)

    // ✅ 이 필드가 있는지 확인하고 없다면 추가한다 이다.
    @Column(name = "identification_result", columnDefinition = "TEXT")
    private String identificationResult;

    // ✅ 업데이트 메서드 추가
    public void updateNickname(String nickname) {
        if (nickname != null && !nickname.trim().isEmpty()) {
            this.nickname = nickname;
        }
    }

    public void updateAdoptionDate(LocalDate adoptionDate) {
        if (adoptionDate != null) {
            this.adoptionDate = adoptionDate;
        }
    }
}