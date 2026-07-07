package com.finte.sigapp.repository;

import com.finte.sigapp.model.PersonalModel;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PersonalRepository extends JpaRepository<PersonalModel, Long> {

    @Query(value = """
            SELECT PERSCODI, PERSNOMB, PERSAPEL, PERSCOEL, PERSDIVI, PERSESTA
              FROM PERSONAL
             WHERE PERSESTA = 'ac'
                AND (:nombre IS NULL OR UPPER(PERSNOMB) LIKE UPPER(:nombre))
                AND (:apellido IS NULL OR UPPER(PERSAPEL) LIKE UPPER(:apellido))
                AND (:cedula IS NULL OR PERSCODI LIKE :cedula)
            """, nativeQuery = true)
    public List<PersonalModel> buscarDinamica(@Param("nombre") String nombre,
            @Param("apellido") String apellido,
            @Param("cedula") String cedula);
}
