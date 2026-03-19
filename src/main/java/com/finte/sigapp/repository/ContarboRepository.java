package com.finte.sigapp.repository;

import com.finte.sigapp.entity.ContarboEntity;
import com.finte.sigapp.entity.ContarboPK;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface ContarboRepository extends JpaRepository<ContarboEntity, ContarboPK> {
    @Query(value = """
        SELECT C
        FROM CONTARBO
        WHERE C.EMPRESA = :empresa
          AND C.BODEGA = :bodega
          AND C.FECHA BETWEEN TO_DATE(:FECHA' 00:00:00','DD/MM/YYYY') AND TO_DATE(:FECHA' 23:59:59','DD/MM/YYYY')
          AND C.ESTADO = 'ac'
          ORDER BY C.ARTICULO
        """,nativeQuery = true)
    List<ContarboEntity> obtenerArticulosOrdenados(String empresa,
                                                   String bodega,
                                                   LocalDateTime fecha);


}