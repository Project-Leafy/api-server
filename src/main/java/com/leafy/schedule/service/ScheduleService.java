package com.leafy.schedule.service;

import com.leafy.global.exception.EntityNotFoundException;
import com.leafy.global.type.WaterFrequency;
import com.leafy.notification.service.KakaoMessageService;
import com.leafy.plant.domain.MyPlant;
import com.leafy.plant.dto.PlantDataDto;
import com.leafy.plant.repository.MyPlantRepository;
import com.leafy.plant.service.PlantDataCache;
import com.leafy.schedule.domain.Schedule;
import com.leafy.schedule.dto.ScheduleRequest;
import com.leafy.schedule.dto.ScheduleResponse;
import com.leafy.schedule.dto.ScheduleSetupRequest;
import com.leafy.schedule.repository.ScheduleRepository;
import com.leafy.user.domain.User;
import com.leafy.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ScheduleService {

    private final ScheduleRepository scheduleRepository;
    private final MyPlantRepository myPlantRepository;
    private final UserRepository userRepository;
    private final KakaoMessageService kakaoMessageService;
    private final PlantDataCache plantDataCache; // PlantDataCache 주입

    @Transactional(readOnly = true)
    public List<ScheduleResponse> getMySchedules() {
        User currentUser = getCurrentUser();
        return scheduleRepository.findAllByUserId(currentUser.getUserId()).stream()
                .map(ScheduleResponse::new)
                .collect(Collectors.toList());
    }

    public Long addSchedule(ScheduleRequest request) {
        User currentUser = getCurrentUser();
        MyPlant myPlant = myPlantRepository.findById(request.getPlantId())
                .orElseThrow(() -> new IllegalArgumentException("식물을 찾을 수 없습니다. ID: " + request.getPlantId()));

        if (!myPlant.getUser().getUserId().equals(currentUser.getUserId())) {
            throw new IllegalArgumentException("본인의 식물에만 일정을 추가할 수 있습니다.");
        }

        Schedule schedule = Schedule.builder()
                .myPlant(myPlant)
                .scheduleType(request.getScheduleType())
                .nextDueDate(request.getNextDueDate())
                .frequencyDays(request.getFrequencyDays())
                .notificationStatus("PENDING")
                .build();
        Schedule savedSchedule = scheduleRepository.save(schedule);

        try {
            String typeKorean = convertTypeToKorean(request.getScheduleType());
            String message = String.format("✅ [Leafy 일정 등록]\n\n'%s'의 '%s' 일정이 등록되었습니다!\n\n📅 날짜: %s",
                    myPlant.getNickname(), typeKorean, request.getNextDueDate());
            kakaoMessageService.sendSelfMessage(currentUser, message);
        } catch (Exception e) {
            log.error("일정 등록 알림 발송 실패: {}", e.getMessage());
        }

        return savedSchedule.getScheduleId();
    }
    
    public void createInitialSchedules(List<ScheduleSetupRequest> requests) {
        User user = getCurrentUser();
        for (ScheduleSetupRequest request : requests) {
            MyPlant myPlant = myPlantRepository.findById(request.getPlantId())
                    .orElseThrow(() -> new EntityNotFoundException("Plant not found with id: " + request.getPlantId()));
            
            if (!myPlant.getUser().getUserId().equals(user.getUserId())) {
                log.warn("User {} attempted to create schedule for plant {} which they do not own.", user.getUserId(), myPlant.getPlantId());
                continue;
            }

            Integer frequencyDays = null;
            if ("AUTO".equals(request.getMode())) {
                log.info("[Schedule] 'AUTO' 모드로 {}의 {} 스케줄 생성 시도", myPlant.getNickname(), request.getScheduleType());
                frequencyDays = getAutoFrequencyDays(myPlant, request.getScheduleType());
            } else { // MANUAL
                log.info("[Schedule] 'MANUAL' 모드로 {}의 {} 스케줄 생성 시도 (주기: {}일)", myPlant.getNickname(), request.getScheduleType(), request.getFrequencyDays());
                frequencyDays = request.getFrequencyDays();
            }

            if (frequencyDays != null) {
                saveInitialSchedule(myPlant, request.getScheduleType(), frequencyDays);
            }
        }
    }

    private Integer getAutoFrequencyDays(MyPlant myPlant, String scheduleType) {
        String speciesName = myPlant.getPlantSpecies().getKoreanName();
        PlantDataDto plantData = plantDataCache.getAllPlants().stream()
                .filter(p -> p.getKoreanName().equals(speciesName))
                .findFirst()
                .orElse(null);

        if (plantData == null) {
            log.warn("[Schedule] PlantDataCache에서 '{}'에 대한 데이터를 찾을 수 없어 AUTO 모드를 적용할 수 없습니다.", speciesName);
            return null;
        }

        switch (scheduleType) {
            case "WATERING":
                Month currentMonth = LocalDate.now().getMonth();
                if (currentMonth == Month.MARCH || currentMonth == Month.APRIL || currentMonth == Month.MAY) return plantData.getWaterSpring();
                if (currentMonth == Month.JUNE || currentMonth == Month.JULY || currentMonth == Month.AUGUST) return plantData.getWaterSummer();
                if (currentMonth == Month.SEPTEMBER || currentMonth == Month.OCTOBER || currentMonth == Month.NOVEMBER) return plantData.getWaterAutumn();
                return plantData.getWaterWinter();
            case "REPOTTING":
                return plantData.getRepottingCycleYears() * 365;
            case "FERTILIZING":
                return plantData.getFertilizerCycleDays();
            default:
                return null;
        }
    }

    private void saveInitialSchedule(MyPlant myPlant, String type, int frequencyDays) {
        if (frequencyDays <= 0) {
            log.info("[Schedule] 주기가 0일 이하({}일)이므로 {} 타입 스케줄을 생성하지 않습니다.", frequencyDays, type);
            return;
        }
        Schedule schedule = Schedule.builder()
                .myPlant(myPlant)
                .scheduleType(type)
                .frequencyDays(frequencyDays)
                .nextDueDate(LocalDate.now().plusDays(frequencyDays))
                .notificationStatus("PENDING")
                .build();
        scheduleRepository.save(schedule);
        log.info("[Schedule] 저장 완료: Plant='{}', Type={}, Frequency={}일", myPlant.getNickname(), type, frequencyDays);
    }
    
    public void deleteSchedule(Long scheduleId) {
        User currentUser = getCurrentUser();
        Schedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new IllegalArgumentException("일정을 찾을 수 없습니다."));

        if (!schedule.getMyPlant().getUser().getUserId().equals(currentUser.getUserId())) {
            throw new IllegalArgumentException("삭제 권한이 없습니다.");
        }
        scheduleRepository.delete(schedule);
    }
    
    private User getCurrentUser() {
        String principalName = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(principalName)
                .orElseThrow(() -> new EntityNotFoundException("User not found with email: " + principalName));
    }

    private String convertTypeToKorean(String type) {
        if (type == null) return "관리";
        return switch (type.toUpperCase()) {
            case "WATER", "WATERING" -> "물주기";
            case "REPOT", "REPOTTING" -> "분갈이";
            case "FERTILIZE", "FERTILIZING" -> "비료주기";
            case "PRUNE", "PRUNING" -> "가지치기";
            default -> "관리";
        };
    }
}