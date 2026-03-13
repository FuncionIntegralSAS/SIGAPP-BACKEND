package com.finte.sigapp.repository;

import com.finte.sigapp.entity.EmpresaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EmpresaJpaRepository extends JpaRepository<EmpresaEntity, String> {

    @Query(value = """
        SELECT T.EMPRCODI, T.EMPRDESC, T.EMPRNIT, T.EMPRESTA 
        FROM empresa T""", nativeQuery = true)
    List<EmpresaEntity> buscarTodas();
}
