package com.finte.sigapp.repository;

import com.finte.sigapp.entity.RoleformPK;
import com.finte.sigapp.entity.RoleformEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RoleformRepository extends JpaRepository<RoleformEntity, RoleformPK> {

  /**
   * Permisos de formas del usuario: los asignados directamente (ROFOTIRO = 'U')
   * y los heredados de los roles a los que pertenece.
   */
  @Query(value = """
      SELECT RF.ROFOROLE,
             RF.ROFOFORM,
             RF.ROFOTIRO,
             RF.ROFOTIFO,
             RF.ROFOPROD
        FROM FPL_ROLEFORM RF
       WHERE RF.ROFOPROD = 'SIGAP'
         AND RF.ROFOTIRO IN ('U')
         AND RF.ROFOTIFO = 'F'
         AND UPPER(TRIM(RF.ROFOROLE)) = UPPER(TRIM(:usuario))
      UNION ALL
      SELECT FO.ROFOROLE,
             RF.ROFOFORM,
             FO.ROFOTIRO,
             FO.ROFOTIFO,
             FO.ROFOPROD
        FROM FPL_ROLEFORM RF
        JOIN FPL_ROLEFORM FO ON (RF.ROFOPROD = FO.ROFOPROD AND RF.ROFOROLE = FO.ROFOFORM)
       WHERE RF.ROFOPROD = 'SIGAP'
         AND RF.ROFOTIRO != ('U')
         AND RF.ROFOTIFO = 'F'
         AND UPPER(TRIM(FO.ROFOROLE)) = UPPER(TRIM(:usuario))
      """, nativeQuery = true)
  List<RoleformEntity> findPermisosByUsuario(@Param("usuario") String usuario);
}
