package com.finte.sigapp.repository;

import com.finte.sigapp.entity.MovitrasEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Acceso por clave a FI_MOVITRAS.
 *
 * {@link MovitrasEntity} mapea solo columnas fisicas, por lo que aqui si
 * funcionan los metodos derivados de JpaRepository. Se usa para el detalle de un
 * traspaso, que es el unico caso donde interesa traer las firmas (CLOB).
 */
@Repository
public interface MovitrasRepository extends JpaRepository<MovitrasEntity, Long> {
}
