package com.finte.sigapp.service.impl;

import com.finte.sigapp.dto.response.PendienteArticuloResponse;
import com.finte.sigapp.entity.FicofiarasEntity2;
import com.finte.sigapp.repository.FicofiarasRepository;
import com.finte.sigapp.service.PendienteArticulosService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PendienteArticulosServiceImpl implements PendienteArticulosService {

    private final FicofiarasRepository ficofiarasRepository;

    @Override
    public List<PendienteArticuloResponse> obtenerPendientes() {
        List<FicofiarasEntity2> entidades = ficofiarasRepository.findPendientes();
        return entidades.stream().map(this::mapToDto).collect(Collectors.toList());
    }

    @Override
    public List<PendienteArticuloResponse> obtenerPendientesPorUsuario(Long idUsuario) {
        List<FicofiarasEntity2> entidades = ficofiarasRepository.findPendientesPorUsuario(idUsuario);
        return entidades.stream().map(this::mapToDto).collect(Collectors.toList());
    }

    private PendienteArticuloResponse mapToDto(FicofiarasEntity2 e) {
        PendienteArticuloResponse dto = new PendienteArticuloResponse();
        dto.setNumeroConteo(e.getARASNUCO());
        dto.setCodigoQr(e.getARASCOQR());
        dto.setCantidadContada(e.getARASCANT());
        dto.setEstado(e.getARASESTA());
        dto.setIdBodega(e.getARASIDBO());
        dto.setIdArticulo(e.getARASIDAR());
        dto.setIdUsuario(e.getARASIDUS());
        dto.setDescripcion(e.getARTIDESC());

        return dto;
    }
}
