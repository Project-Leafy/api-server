package com.leafy.plant.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leafy.diagnosis.domain.DiagnosisHistory;
import com.leafy.diagnosis.repository.DiagnosisHistoryRepository;
import com.leafy.plant.dto.*;
import com.leafy.global.type.PlantStatus;
import lombok.extern.slf4j.Slf4j;
import com.leafy.global.exception.EntityNotFoundException;
import com.leafy.plant.domain.MyPlant;
import com.leafy.plant.domain.PlantSpecies;
import com.leafy.plant.repository.MyPlantRepository;
import com.leafy.plant.repository.PlantSpeciesRepository;
import com.leafy.schedule.service.ScheduleService;
import com.leafy.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Month;
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
    private final PlantDataCache plantDataCache; // 의존성 추가

    // [신규] 추천 관리 정보 조회
    public CareInfoResponse getCareInfo(Long speciesId) {
        PlantSpecies species = plantSpeciesRepository.findById(speciesId)
                .orElseThrow(() -> new EntityNotFoundException("PlantSpecies not found with id: " + speciesId));

        PlantDataDto plantData = plantDataCache.getAllPlants().stream()
                .filter(p -> p.getKoreanName().equals(species.getKoreanName()))
                .findFirst()
                .orElseThrow(() -> new EntityNotFoundException("Care info not found in cache for: " + species.getKoreanName()));

        // 현재 계절에 맞는 물주기 주기 선택
        int waterCycle;
        Month currentMonth = LocalDate.now().getMonth();
        if (currentMonth == Month.MARCH || currentMonth == Month.APRIL || currentMonth == Month.MAY) waterCycle = plantData.getWaterSpring();
        else if (currentMonth == Month.JUNE || currentMonth == Month.JULY || currentMonth == Month.AUGUST) waterCycle = plantData.getWaterSummer();
        else if (currentMonth == Month.SEPTEMBER || currentMonth == Month.OCTOBER || currentMonth == Month.NOVEMBER) waterCycle = plantData.getWaterAutumn();
        else waterCycle = plantData.getWaterWinter();

        return CareInfoResponse.builder()
                .waterCycle(waterCycle)
                .fertilizerCycle(plantData.getFertilizerCycleDays())
                .repotCycle(plantData.getRepottingCycleYears() * 365) // 년 -> 일 변환
                .build();
    }

    public List<MyPlantResponseDto> findMyPlants(User user) {
        return myPlantRepository.findAllByUserOrderByCreatedAtDesc(user).stream()
                .map(MyPlantResponseDto::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public MyPlantResponseDto join(User user, CreateMyPlantRequest request) {
        PlantSpecies species = plantSpeciesRepository.findById(request.speciesId())
                .orElseThrow(() -> new EntityNotFoundException("Plant Species not found with id: " + request.speciesId()));

        LocalDate adoptionDate = request.adoptionDate() != null ? request.adoptionDate() : LocalDate.now();

        MyPlant myPlant = MyPlant.builder()
                .user(user)
                .plantSpecies(species)
                .nickname(request.nickname())
                .imageUrl(request.imageUrl())
                .adoptionDate(adoptionDate)
                .identificationResult(request.identificationResult())
                .build();

        MyPlant savedPlant = myPlantRepository.save(myPlant);
        return MyPlantResponseDto.from(savedPlant);
    }

    @Transactional
    public void delete(Long plantId, User user) {
        MyPlant myPlant = myPlantRepository.findById(plantId)
                .orElseThrow(() -> new EntityNotFoundException("Plant not found with id: " + plantId));

        if (!myPlant.getUser().getUserId().equals(user.getUserId())) {
            throw new IllegalArgumentException("User does not own this plant.");
        }

        myPlantRepository.delete(myPlant);
    }

    public PlantDetailResponseDto getMyPlantDetail(Long plantId) {
        MyPlant myPlant = myPlantRepository.findById(plantId)
                .orElseThrow(() -> new EntityNotFoundException("해당 식물을 찾을 수 없습니다. ID: " + plantId));

        Optional<DiagnosisHistory> latestHistory = diagnosisHistoryRepository
                .findTopByMyPlantOrderByDiagnosisDatetimeDesc(myPlant);

        return PlantDetailResponseDto.of(myPlant, latestHistory, objectMapper);
    }

    @Transactional
    public MyPlantResponseDto updateMyPlant(Long plantId, UpdateMyPlantRequest request, User user) {
        MyPlant myPlant = myPlantRepository.findById(plantId)
                .orElseThrow(() -> new EntityNotFoundException("식물을 찾을 수 없습니다."));

        if (!myPlant.getUser().getUserId().equals(user.getUserId())) {
            throw new IllegalArgumentException("수정 권한이 없습니다.");
        }

        if (request.getNickname() != null && !request.getNickname().isBlank()) {
            myPlant.updateNickname(request.getNickname());
        }

        if (request.getAdoptionDate() != null) {
            myPlant.updateAdoptionDate(request.getAdoptionDate());
        }

        return MyPlantResponseDto.from(myPlant);
    }

    @Transactional
    public MyPlantResponseDto updateMyPlantStatus(Long myPlantId, PlantStatus newStatus, User user) {
        MyPlant myPlant = myPlantRepository.findById(myPlantId)
                .orElseThrow(() -> new EntityNotFoundException("식물을 찾을 수 없습니다. ID: " + myPlantId));

        if (!myPlant.getUser().getUserId().equals(user.getUserId())) {
            throw new IllegalArgumentException("권한이 없습니다. 본인 식물만 상태를 변경할 수 있습니다.");
        }

        if (newStatus == PlantStatus.SICK) {
            throw new IllegalArgumentException("식물의 상태를 SICK으로 직접 변경할 수 없습니다. 진단 서비스를 이용해주세요.");
        }
        
        myPlant.updateStatus(newStatus);
        MyPlant savedMyPlant = myPlantRepository.save(myPlant);
        return MyPlantResponseDto.from(savedMyPlant);
    }

    public PlantStatus getMyPlantStatus(Long myPlantId, User user) {
        MyPlant myPlant = myPlantRepository.findById(myPlantId)
                .orElseThrow(() -> new EntityNotFoundException("식물을 찾을 수 없습니다. ID: " + myPlantId));

        if (!myPlant.getUser().getUserId().equals(user.getUserId())) {
            throw new IllegalArgumentException("권한이 없습니다. 본인 식물만 상태를 조회할 수 있습니다.");
        }
        return myPlant.getStatus();
    }
}
