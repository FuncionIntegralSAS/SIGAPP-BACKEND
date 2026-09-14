package com.finte.sigapp.repository;

import com.finte.sigapp.model.PlacaRequisicionId;
import com.finte.sigapp.model.PlacaRequisicionModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

/**
 * Lectura de las placas asignadas a las lineas de una requisicion (ASPLMORS).
 *
 * Se exponen en el detalle porque el ERP exige que su numero coincida con la
 * cantidad que se mueve: si no cuadra, el registro falla despues de haber
 * recogido las dos firmas. Verlas antes evita ese callejon.
 *
 * Solo lectura desde JPA. Asignar y soltar placas lo hace el paquete.
 */
@Repository
public interface PlacaRequisicionRepository extends JpaRepository<PlacaRequisicionModel, PlacaRequisicionId> {

    /**
     * Las placas de una requisicion, EN ORDEN DE DESPACHO.
     *
     * El orden no es cosmetico: es el mismo criterio con el que el paquete las
     * eligio -fecha de entrada del activo, y la placa como desempate-, asi que
     * la lista que ve quien despacha coincide con el orden en que deberia sacar
     * los elementos del estante. Devolverlas en cualquier otro orden obligaria a
     * reconstruir mentalmente el FIFO.
     *
     * El JOIN es LEFT porque un articulo de consumo no tiene fila en ACTIFIJO;
     * en ese caso no hay placas y la consulta no devuelve nada, pero la forma
     * del SQL no debe depender de eso. Se acota por empresa para que el mismo
     * par articulo-placa de otra compania no duplique filas.
     */
    @Query(value = """
            SELECT A.ASPLTDEM, A.ASPLTIDO, A.ASPLNUME, A.ASPLSECU, A.ASPLARTI, A.ASPLPLAC
              FROM ASPLMORS A
              LEFT JOIN ACTIFIJO F ON F.ACFIARTI = A.ASPLARTI
                                  AND F.ACFIPLAC = A.ASPLPLAC
                                  AND F.ACFIDIEM = A.ASPLTDEM
             WHERE A.ASPLTDEM = :empresa
               AND A.ASPLTIDO = :tipoDocumento
               AND A.ASPLNUME = :numero
             ORDER BY A.ASPLARTI, A.ASPLSECU, F.ACFIFEEN, A.ASPLPLAC
            """, nativeQuery = true)
    List<PlacaRequisicionModel> buscarPorRequisicion(@Param("empresa") String empresa,
                                                     @Param("tipoDocumento") String tipoDocumento,
                                                     @Param("numero") BigDecimal numero);
}
