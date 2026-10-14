package com.finte.sigapp.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import com.finte.sigapp.model.UsabadaModel;

@Repository
public interface UsabadaRepository extends JpaRepository<UsabadaModel, String> {

    @Query(value = """
            SELECT USBDCODI,USBDDESC,SRF_PKGENCRIPTAR.funDesencriptar(USBDCONT) AS USBDCONT,
                   USBDEXCO,USBDINFA,USBDESTA,PERSCODI
              FROM SRF_USUABADA
              JOIN SRF_PERSONAL p ON  usbdcodi = persusua
             WHERE USBDCODI = :username """, nativeQuery = true)
    public Optional<UsabadaModel> buscarPorUsername(@Param("username") String username);

}