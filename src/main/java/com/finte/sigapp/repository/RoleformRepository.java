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
          select rf.roforole,
          rf.rofoform,
          rf.rofotiro,
          rf.rofotifo,
          rf.rofoprod
      from fpl_roleform rf
      where 1=1
      and rf.rofotiro in ( 'U' )
      and rf.rofotifo = 'F'
      and upper(trim(rf.roforole)) = upper(trim(:usuario))
      union all
      select fo.roforole,
          rf.rofoform,
          fo.rofotiro,
          fo.rofotifo,
          fo.rofoprod
      from fpl_roleform rf
      join fpl_roleform fo
      on ( rf.rofoprod = fo.rofoprod
      and rf.roforole = fo.rofoform )
      where 1=1
      and rf.rofotiro != ( 'U' )
      and rf.rofotifo = 'F'
      and upper(trim(fo.roforole)) = upper(trim(:usuario))
            """, nativeQuery = true)
  List<RoleformEntity> findPermisosByUsuario(@Param("usuario") String usuario);
}
