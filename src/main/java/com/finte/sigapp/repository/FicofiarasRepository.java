package com.finte.sigapp.repository;

import com.finte.sigapp.entity.FicofiarasEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface FicofiarasRepository extends JpaRepository<FicofiarasEntity,Long> {
    @Query(value = "SELECT SEQ_COFIARAS.NEXTVAL FROM DUAL", nativeQuery = true)
    Long obtenerSiguienteId();
    @Query(value = "SELECT SEQ_COFIASIG.NEXTVAL FROM DUAL", nativeQuery = true)
    Long obtenerIdConteo();
}
