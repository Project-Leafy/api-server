package com.leafy.plant.repository;

import com.leafy.plant.domain.NongSaro;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface NongSaroRepository extends JpaRepository<NongSaro, Long> {

    Optional<NongSaro> findByCntntsNo(String cntntsNo);

}
