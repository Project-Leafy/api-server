package com.leafy.plant.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leafy.diagnosis.domain.DiagnosisHistory;
import com.leafy.diagnosis.repository.DiagnosisHistoryRepository;
import com.leafy.plant.dto.PlantDetailResponseDto; // ✅ 추가 (반환 타입 변경)
import com.leafy.global.type.PlantStatus; // PlantStatus import 추가
import com.leafy.diagnosis.domain.DiagnosisHistory; // ✅ 추가 (Optional 사용)
import lombok.extern.slf4j.Slf4j; // ✅ 추가
import com.leafy.global.exception.EntityNotFoundException;
import com.leafy.plant.domain.MyPlant;
import com.leafy.plant.domain.PlantSpecies;
import com.leafy.plant.dto.CreateMyPlantRequest;
import com.leafy.plant.dto.MyPlantResponseDto;
import com.leafy.plant.dto.PlantDetailResponseDto;
import com.leafy.plant.dto.UpdateMyPlantRequest; // 👈 DTO 임포트
import com.leafy.plant.repository.MyPlantRepository;
import com.leafy.plant.repository.PlantSpeciesRepository;
import com.leafy.schedule.service.ScheduleService;
import com.leafy.user.domain.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MyPlantService {

    private final MyPlantRepository myPlantRepository;
    private final PlantSpeciesRepository plantSpeciesRepository;
    private final ScheduleService scheduleService;
    private final DiagnosisHistoryRepository diagnosisHistoryRepository;
    private final ObjectMapper objectMapper;

    public List<MyPlantResponseDto> findMyPlants(User user) {
        return myPlantRepository.findAllByUserOrderByCreatedAtDesc(user).stream()
                .map(MyPlantResponseDto::from)
                .collect(Collectors.toList());
    }

    /**
     * 식물 최종 등록 (저장)
     */
    @Transactional
    public MyPlantResponseDto join(User user, CreateMyPlantRequest request) {
        // 1. 식물 종 정보 조회
        PlantSpecies species = plantSpeciesRepository.findById(request.speciesId())
                .orElseThrow(() -> new EntityNotFoundException("Plant Species not found with id: " + request.speciesId()));

        // 2. 입양일 설정
        LocalDate adoptionDate = request.adoptionDate() != null ? request.adoptionDate() : LocalDate.now();

        // 3. 내 식물(MyPlant) 객체 생성
        MyPlant myPlant = MyPlant.builder()
                .user(user)
                .plantSpecies(species)
                .nickname(request.nickname())
                .imageUrl(request.imageUrl())
                .adoptionDate(adoptionDate)
                .identificationResult(request.identificationResult())
                .build();

        // 4. DB 저장
        MyPlant savedPlant = myPlantRepository.save(myPlant);

        // 5. 스케줄 자동 생성 (필요시 주석 해제)
        // scheduleService.createInitialSchedule(savedPlant);

        return MyPlantResponseDto.from(savedPlant);
    }

    /**
     * 식물 삭제 (소유자 확인 포함)
     */
    @Transactional
    public void delete(Long plantId, User user) {
        MyPlant myPlant = myPlantRepository.findById(plantId)
                .orElseThrow(() -> new EntityNotFoundException("Plant not found with id: " + plantId));

        if (!myPlant.getUser().getUserId().equals(user.getUserId())) {
            throw new IllegalArgumentException("User does not own this plant.");
        }

        myPlantRepository.delete(myPlant);
    }

    /**
     * 식물 상세 조회
     */
    public PlantDetailResponseDto getMyPlantDetail(Long plantId) {
        MyPlant myPlant = myPlantRepository.findById(plantId)
                .orElseThrow(() -> new EntityNotFoundException("해당 식물을 찾을 수 없습니다. ID: " + plantId));

        Optional<DiagnosisHistory> latestHistory = diagnosisHistoryRepository
                .findTopByMyPlantOrderByDiagnosisDatetimeDesc(myPlant);

        return PlantDetailResponseDto.of(myPlant, latestHistory, objectMapper);
    }

    /**
     * 식물 정보 업데이트 (닉네임, 입양일)
     * ✅ Map 대신 DTO(UpdateMyPlantRequest)를 받도록 수정됨
     */
    @Transactional
    public MyPlantResponseDto updateMyPlant(Long plantId, UpdateMyPlantRequest request, User user) {
        MyPlant myPlant = myPlantRepository.findById(plantId)
                .orElseThrow(() -> new EntityNotFoundException("식물을 찾을 수 없습니다."));

        // 본인 식물인지 확인
        if (!myPlant.getUser().getUserId().equals(user.getUserId())) {
            throw new IllegalArgumentException("수정 권한이 없습니다.");
        }

        // 닉네임 변경 (값이 있을 때만)
        if (request.getNickname() != null && !request.getNickname().isBlank()) {
            myPlant.updateNickname(request.getNickname());
        }

        // 입양일 변경 (값이 있을 때만)
        if (request.getAdoptionDate() != null) {
            myPlant.updateAdoptionDate(request.getAdoptionDate());
        }

        return MyPlantResponseDto.from(myPlant);
    }

    /**
     * 반려식물 상태 업데이트 (HEALTHY 또는 WITHERED)
     */
    @Transactional
    public MyPlantResponseDto updateMyPlantStatus(Long myPlantId, PlantStatus newStatus, User user) {
        MyPlant myPlant = myPlantRepository.findById(myPlantId)
                .orElseThrow(() -> new EntityNotFoundException("식물을 찾을 수 없습니다. ID: " + myPlantId));

        if (!myPlant.getUser().getUserId().equals(user.getUserId())) {
            throw new IllegalArgumentException("권한이 없습니다. 본인 식물만 상태를 변경할 수 있습니다.");
        }

        // HEALTHY 또는 WITHERED만 허용 (SICK은 진단 서비스에서 자동 설정)
        if (newStatus == PlantStatus.SICK) {
            throw new IllegalArgumentException("식물의 상태를 SICK으로 직접 변경할 수 없습니다. 진단 서비스를 이용해주세요.");
        }
        
        myPlant.updateStatus(newStatus); // MyPlant 엔티티의 updateStatus 메서드 호출

        // 변경된 MyPlant 엔티티 저장 (더티 체킹으로 자동 반영될 수 있으나 명시적으로 저장)
        MyPlant savedMyPlant = myPlantRepository.save(myPlant);
        return MyPlantResponseDto.from(savedMyPlant);
    }

    /**
     * 반려식물 상태 조회
     */
    public PlantStatus getMyPlantStatus(Long myPlantId, User user) {
        MyPlant myPlant = myPlantRepository.findById(myPlantId)
                .orElseThrow(() -> new EntityNotFoundException("식물을 찾을 수 없습니다. ID: " + myPlantId));

        if (!myPlant.getUser().getUserId().equals(user.getUserId())) {
            throw new IllegalArgumentException("권한이 없습니다. 본인 식물만 상태를 조회할 수 있습니다.");
        }
        return myPlant.getStatus();
    }

}
