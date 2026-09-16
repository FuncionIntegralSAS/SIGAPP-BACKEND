package com.finte.sigapp.repository;

import com.finte.sigapp.entity.AcfigeolEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AcfigeolJpaRepository extends JpaRepository<AcfigeolEntity, Long> {
}
