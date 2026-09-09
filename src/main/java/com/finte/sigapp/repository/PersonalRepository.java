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

    /**
     * Personas asociadas a una bodega, para elegir fuente o destino de un
     * traspaso.
     *
     * El vinculo no es directo: una bodega pertenece a una division (BODEDIVI) y
     * una persona tambien (PERSDIVI). Esta consulta es el camino inverso de
     * fun_bodega_persona del paquete PKG_FI_MOVITRAS, que dada una persona
     * resuelve su bodega.
     *
     * La empresa es obligatoria porque la llave de BODEGA es (BODECODI,
     * BODEEMPR): sin ella el mismo codigo de bodega puede existir en varias
     * empresas y resolver a divisiones distintas.
     */
    @Query(value = """
            SELECT P.PERSCODI, P.PERSNOMB, P.PERSAPEL, P.PERSCOEL, P.PERSDIVI, P.PERSESTA
              FROM PERSONAL P
              JOIN BODEGA B
                ON B.BODEDIVI = P.PERSDIVI
             WHERE B.BODECODI = :bodega
               AND B.BODEEMPR = :empresa
               AND P.PERSESTA = 'ac'
             ORDER BY P.PERSNOMB, P.PERSAPEL
            """, nativeQuery = true)
    public List<PersonalModel> buscarPorBodega(@Param("bodega") String bodega,
            @Param("empresa") String empresa);
}
