package com.finte.sigapp.service.impl;

import com.finte.sigapp.dto.response.PendienteArticuloResponse;
import com.finte.sigapp.entity.FicofiarasEntity;
import com.finte.sigapp.repository.FicofiarasRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PendienteArticulosServiceImplTest {

    @Mock
    private FicofiarasRepository ficofiarasRepository;

    @InjectMocks
    private PendienteArticulosServiceImpl pendienteArticulosService;

    @Test
    @DisplayName("obtenerPendientes maps entity fields to DTO correctly")
    void obtenerPendientes_mapsFields() {
        // Arrange: create mock entities
        FicofiarasEntity e1 = new FicofiarasEntity();
        e1.setARASNUCO(1);
        e1.setARASCOQR("QR123");
        e1.setARASCANT(100L);
        e1.setARASESTA("PE");
        e1.setARASIDBO("B01");
        e1.setARASIDAR("A01");
        e1.setARASIDUS(10L);

        FicofiarasEntity e2 = new FicofiarasEntity();
        e2.setARASNUCO(2);
        e2.setARASCOQR("QR456");
        e2.setARASCANT(200L);
        e2.setARASESTA("PE");
        e2.setARASIDBO("B02");
        e2.setARASIDAR("A02");
        e2.setARASIDUS(20L);

        when(ficofiarasRepository.findPendientes()).thenReturn(Arrays.asList(e1, e2));

        // Act
        List<PendienteArticuloResponse> result = pendienteArticulosService.obtenerPendientes();

        // Assert
        assertNotNull(result);
        assertEquals(2, result.size());
        PendienteArticuloResponse dto1 = result.get(0);
        assertEquals(1, dto1.getNumeroConteo());
        assertEquals("QR123", dto1.getCodigoQr());
        assertEquals(100L, dto1.getCantidadContada());
        assertEquals("PE", dto1.getEstado());
        assertEquals("B01", dto1.getIdBodega());
        assertEquals("A01", dto1.getIdArticulo());
        assertEquals(10L, dto1.getIdUsuario());

        PendienteArticuloResponse dto2 = result.get(1);
        assertEquals(2, dto2.getNumeroConteo());
        assertEquals("QR456", dto2.getCodigoQr());
        assertEquals(200L, dto2.getCantidadContada());
        assertEquals("PE", dto2.getEstado());
        assertEquals("B02", dto2.getIdBodega());
        assertEquals("A02", dto2.getIdArticulo());
        assertEquals(20L, dto2.getIdUsuario());
    }
}
