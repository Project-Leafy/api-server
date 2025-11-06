package com.leafy.plant.service;

import com.leafy.plant.dto.MyPlantResponseDto;
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
public class MyPlantService {

    private final MyPlantRepository myPlantRepository;

    public List<MyPlantResponseDto> findMyPlants(User user) {
        return myPlantRepository.findAllByUserOrderByCreatedAtDesc(user).stream()
                .map(MyPlantResponseDto::from)
                .collect(Collectors.toList());
    }
}
