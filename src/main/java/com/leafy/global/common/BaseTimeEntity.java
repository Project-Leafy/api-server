// com/leafy/global/common/BaseTimeEntity.java

package com.leafy.global.common;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Getter
@MappedSuperclass // 1. JPA 엔티티 클래스들이 이 클래스를 상속할 경우 필드(createdDate, modifiedDate)도 컬럼으로 인식하도록 합니다.
@EntityListeners(AuditingEntityListener.class) // 2. 이 클래스에 Auditing 기능을 포함시킵니다.
public abstract class BaseTimeEntity {

    @CreatedDate // 3. 엔티티가 생성되어 저장될 때 시간이 자동 저장됩니다.
    @Column(updatable = false, name = "created_at")
    private LocalDateTime createdAt;

    @LastModifiedDate // 4. 조회한 엔티티의 값을 변경할 때 시간이 자동 저장됩니다.
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
