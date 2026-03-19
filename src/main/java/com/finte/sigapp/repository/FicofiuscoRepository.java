package com.finte.sigapp.repository;

import com.finte.sigapp.entity.FicofiuscoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface FicofiuscoRepository extends JpaRepository<FicofiuscoEntity,Long> {

    Optional<FicofiuscoEntity> findByUSCODOCU(String documento);

    @Query(value = "SELECT SEQUSCO.NEXTVAL FROM DUAL", nativeQuery = true)
    Long obtenerSiguienteId();
}
