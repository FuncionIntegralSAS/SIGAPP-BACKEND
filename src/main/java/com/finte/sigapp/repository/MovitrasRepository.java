package com.finte.sigapp.repository;

import com.finte.sigapp.entity.MovitrasEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

/**
 * Acceso por clave a FI_MOVITRAS.
 *
 * {@link MovitrasEntity} mapea solo columnas fisicas y conserva {@code @Id}
 * simple sobre MOTRIDMT, de modo que los metodos derivados siguen funcionando.
 * Pero el acceso normal del modulo NO es por MOTRIDMT: es por numero de tramite,
 * que devuelve las N lineas del traspaso.
 */
@Repository
public interface MovitrasRepository extends JpaRepository<MovitrasEntity, Long> {

    /**
     * Lineas de un traspaso, en el orden en que se cargaron los articulos.
     *
     * Devuelve lista vacia si el tramite no existe. Los datos de cabecera se
     * pueden tomar de cualquiera de las filas: son identicos en todas.
     */
    List<MovitrasEntity> findByMotrnutrOrderByMotridmt(Long motrnutr);

    /**
     * Lineas abiertas que usan alguno de estos articulos.
     *
     * Sirve para avisar antes de crear que un elemento ya esta comprometido. Se
     * filtra solo por el codigo de articulo y el par exacto con la placa se cruza
     * en el service: un mismo articulo puede tener varias placas y son elementos
     * distintos.
     *
     * @param estados los que dejan el traspaso abierto: 'pe' y 'ap'
     */
    List<MovitrasEntity> findByMotrestaInAndMotrartiIn(Collection<String> estados,
                                                       Collection<String> articulos);
}
