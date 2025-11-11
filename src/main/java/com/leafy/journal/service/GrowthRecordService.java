package com.leafy.journal.service;

import com.leafy.global.exception.ResourceNotFoundException;
import com.leafy.journal.domain.GrowthRecord;
import com.leafy.journal.dto.CreateGrowthRecordRequest;
import com.leafy.journal.dto.GrowthRecordResponse;
import com.leafy.journal.dto.UpdateGrowthRecordRequest;
import com.leafy.journal.repository.GrowthRecordRepository;
import com.leafy.plant.domain.MyPlant;
import com.leafy.plant.repository.MyPlantRepository;
import com.leafy.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GrowthRecordService {

    private final GrowthRecordRepository growthRecordRepository;
    private final MyPlantRepository myPlantRepository;

    /**
     * 성장 일지 생성
     */
    @Transactional
    public GrowthRecordResponse create(Long plantId, CreateGrowthRecordRequest request, User user) {
        MyPlant myPlant = myPlantRepository.findById(plantId)
                .orElseThrow(() -> new ResourceNotFoundException("Plant not found with id: " + plantId));

        // 식물 소유권 확인
        if (!myPlant.getUser().getUserId().equals(user.getUserId())) {
            throw new ResourceNotFoundException("User does not own this plant.");
        }

        GrowthRecord growthRecord = GrowthRecord.builder()
                .myPlant(myPlant)
                .recordDate(request.recordDate())
                .photoUrl(request.photoUrl())
                .memo(request.memo())
                .watered(request.watered() != null ? request.watered() : false)
                .fertilized(request.fertilized() != null ? request.fertilized() : false)
                .pruned(request.pruned() != null ? request.pruned() : false)
                .repotted(request.repotted() != null ? request.repotted() : false)
                .waterAmountType(request.waterAmountType())
                .fertilizerType(request.fertilizerType())
                .build();

        GrowthRecord savedRecord = growthRecordRepository.save(growthRecord);
        return GrowthRecordResponse.from(savedRecord);
    }

    /**
     * 특정 식물의 모든 성장 일지 조회
     */
    public List<GrowthRecordResponse> findAllByPlantId(Long plantId, User user) {
        MyPlant myPlant = myPlantRepository.findById(plantId)
                .orElseThrow(() -> new ResourceNotFoundException("Plant not found with id: " + plantId));

        if (!myPlant.getUser().getUserId().equals(user.getUserId())) {
            throw new ResourceNotFoundException("User does not own this plant.");
        }

        return growthRecordRepository.findAllByMyPlantOrderByRecordDateDesc(myPlant)
                .stream()
                .map(GrowthRecordResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 특정 성장 일지 상세 조회
     */
    public GrowthRecordResponse findById(Long recordId, User user) {
        GrowthRecord growthRecord = growthRecordRepository.findById(recordId)
                .orElseThrow(() -> new ResourceNotFoundException("Growth Record not found with id: " + recordId));

        if (!growthRecord.getMyPlant().getUser().getUserId().equals(user.getUserId())) {
            throw new ResourceNotFoundException("User does not own this growth record.");
        }

        return GrowthRecordResponse.from(growthRecord);
    }

    /**
     * 성장 일지 수정
     */
    @Transactional
    public GrowthRecordResponse update(Long recordId, UpdateGrowthRecordRequest request, User user) {
        GrowthRecord growthRecord = growthRecordRepository.findById(recordId)
                .orElseThrow(() -> new ResourceNotFoundException("Growth Record not found with id: " + recordId));

        if (!growthRecord.getMyPlant().getUser().getUserId().equals(user.getUserId())) {
            throw new ResourceNotFoundException("User does not own this growth record.");
        }

        growthRecord.update(
                request.recordDate(),
                request.memo(),
                request.watered(),
                request.fertilized(),
                request.pruned(),
                request.repotted(),
                request.waterAmountType(),
                request.fertilizerType()
        );

        return GrowthRecordResponse.from(growthRecord);
    }

    /**
     * 성장 일지 삭제
     */
    @Transactional
    public void delete(Long recordId, User user) {
        GrowthRecord growthRecord = growthRecordRepository.findById(recordId)
                .orElseThrow(() -> new ResourceNotFoundException("Growth Record not found with id: " + recordId));

        if (!growthRecord.getMyPlant().getUser().getUserId().equals(user.getUserId())) {
            throw new ResourceNotFoundException("User does not own this growth record.");
        }

        growthRecordRepository.delete(growthRecord);
    }
}
