package com.finte.sigapp.service;

import com.finte.sigapp.dto.request.TransferProcessRequest;
import com.finte.sigapp.dto.request.TransferRequest;
import com.finte.sigapp.dto.response.TransferResponse;
import com.finte.sigapp.model.SigappTraspasoModel;
import com.finte.sigapp.repository.TransferRepository;
import com.finte.sigapp.service.impl.TransferServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransferServiceImplTest {

    @Mock
    private TransferRepository repository;

    @InjectMocks
    private TransferServiceImpl service;

    @Test
    void crearSolicitud_DevuelveIdGenerado() {
        TransferRequest req = new TransferRequest();
        req.setEmpresa("EMP");
        req.setTipoMov("TM");

        when(repository.llamarProcedimientoCrear(eq("EMP"), eq("TM"), any(), any(), any(), any(), eq("USER_01")))
                .thenReturn("TRASP_100");

        String res = service.crearSolicitud(req, "USER_01");

        assertNotNull(res);
        assertEquals("TRASP_100", res);

        System.out.println("✅ [crearSolicitud_DevuelveIdGenerado] Éxito. ID devuelto de la BD: " + res);
    }

    @Test
    void crearSolicitud_LanzaExceptionSiProcedimientoFalla() {
        TransferRequest req = new TransferRequest();
        when(repository.llamarProcedimientoCrear(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(null);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.crearSolicitud(req, "USER_01"));
        assertTrue(ex.getMessage().contains("no retornó ID"));

        System.out.println("✅ [crearSolicitud_LanzaException] Excepción controlada: " + ex.getMessage());
    }

    @Test
    void listarSolicitudes_FiltraCorrectamentePorProcesados() {
        SigappTraspasoModel model = new SigappTraspasoModel();
        model.setTrasId(10L); // TrasId es numérico (Long)
        model.setTrasFechCrea(LocalDateTime.now());

        // Verificar que pasa 'ap' y 'na' en la lista cuando estadoFront = 'pr'
        when(repository.listarPorBodegaYEstados(eq("BOD_01"),
                argThat(list -> list.contains("ap") && list.contains("na"))))
                .thenReturn(List.of(model));

        List<TransferResponse> res = service.listarSolicitudes("BOD_01", "pr");

        assertEquals(1, res.size());
        assertEquals(10L, res.get(0).getId()); // TransferResponse getId() debe coincidir con el tipo

        System.out.println("✅ [listarSolicitudes_FiltraCorrectamentePorProcesados] Trámites procesados listados:");
        res.forEach(t -> System.out.println("   -> ID Trámite mapeado: " + t.getId()));
    }

    @Test
    void procesarTraspaso_LanzaExceptionAlRechazarSinObservacion() {
        TransferProcessRequest req = new TransferProcessRequest();
        req.setEstado("na");
        req.setObservacion("   "); // Vacío/espacios

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.procesarTraspaso(1L, req, "USER_01"));

        assertTrue(ex.getMessage().contains("Debe incluir una observación"));
        verify(repository, never()).callApproveRejectProcedure(anyLong(), any(), any(), any());

        System.out.println("✅ [procesarTraspaso_LanzaException] Validada excepción obligando a usar observación: " + ex.getMessage());
    }

    @Test
    void procesarTraspaso_LlamaProcedimientoExitosamente() {
        TransferProcessRequest req = new TransferProcessRequest();
        req.setEstado("ap");
        req.setObservacion("Aprobado según revisión");

        assertDoesNotThrow(() -> service.procesarTraspaso(1L, req, "USER_01"));

        verify(repository, times(1)).callApproveRejectProcedure(1L, "ap", "Aprobado según revisión", "USER_01");

        System.out.println("✅ [procesarTraspaso_LlamaProcedimientoExitosamente] El traspaso fue procesado delegando a Oracle BD con éxito.");
    }
}
