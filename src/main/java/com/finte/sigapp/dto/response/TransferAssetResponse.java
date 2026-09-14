package com.finte.sigapp.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Activo del que responde una persona, para el selector de articulos al crear un
 * traspaso.
 *
 * {@code articulo} y {@code placa} son exactamente lo que hay que enviar en
 * {@code articulos[]} de {@code POST /api/v1/traspasos/crear}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferAssetResponse {

    private String articulo;
    private String placa;
    private String nombre;

    /**
     * Centro de informacion y tercero del activo.
     *
     * Un traspaso NO puede mezclar articulos con valores distintos en estos dos
     * campos: son datos de cabecera del documento y el paquete rechaza el segundo
     * articulo que no coincida. Vienen aqui para que el front lo impida en el
     * selector en vez de descubrirlo con un error al crear.
     */
    private String centroInformacion;
    private String tercero;

    /**
     * True si el elemento ya esta dentro de un traspaso en estado {@code pe} o
     * {@code ap}. No se excluye de la lista: se marca, para que el usuario
     * entienda por que no puede seleccionarlo.
     */
    private boolean enTramite;
}
