package com.finte.sigapp.repository;

import com.finte.sigapp.entity.BodegaEntity;
import com.finte.sigapp.entity.BodegaId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BodegaJpaRepository extends JpaRepository<BodegaEntity, BodegaId> {

    @Query(value = """
            SELECT T.BODECODI,T.BODEEMPR, T.BODEDESC, T.BODEESTA 
            FROM bodega T 
            WHERE T.BODEEMPR = :EMPRESA""", nativeQuery = true)
    List<BodegaEntity> findByBodeEmpr(@Param("EMPRESA") String empresa);
}
