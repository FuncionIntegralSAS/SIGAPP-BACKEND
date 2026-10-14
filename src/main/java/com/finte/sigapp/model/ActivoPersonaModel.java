package com.finte.sigapp.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Activo asignado a una persona, tal como lo necesita el formulario de traspaso.
 *
 * Es una vista de lectura sobre ACTIFIJO: ademas de las columnas del activo trae
 * ENTRAMITE, que no existe fisicamente y dice si ese elemento ya esta dentro de
 * un traspaso sin cerrar.
 *
 * La clave es el par (articulo, placa) y no ACFIIDIN porque es la misma pareja
 * con la que el modulo identifica un elemento en todas partes. La consulta
 * devuelve la placa con NVL a punto, de modo que ninguna de las dos columnas
 * llegue nula: un componente nulo en la clave rompe el mapeo.
 *
 * Solo debe consultarse con la query nativa de
 * {@link com.finte.sigapp.repository.ActivoPersonaRepository}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@IdClass(ActivoPersonaId.class)
@Table(name = "SRF_ACTIFIJO")
public class ActivoPersonaModel {

    @Id
    @Column(name = "ACFIARTI")
    private String acfiarti; // Codigo del articulo

    @Id
    @Column(name = "ACFIPLAC")
    private String acfiplac; // Placa. Punto cuando el elemento no tiene

    @Column(name = "ACFIOBSE")
    private String acfiobse; // Descripcion del elemento

    /*
     * Centro de informacion y tercero. Se traen porque son datos de CABECERA del
     * documento: un traspaso no puede mezclar articulos con valores distintos, y
     * el paquete lo rechaza al agregar. Con estos dos campos el front puede
     * impedir la seleccion antes de llamar a la API.
     */

    @Column(name = "ACFICEIN")
    private String acficein;

    @Column(name = "ACFITERC")
    private String acfiterc;

    /** 1 si el elemento ya esta en un traspaso en estado pe o ap. */
    @Column(name = "ENTRAMITE")
    private Integer enTramite;
}
