package com.finte.sigapp.service.impl;

import com.finte.sigapp.dto.response.PendienteArticuloResponse;
import com.finte.sigapp.entity.FicofiarasEntity;
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
        List<FicofiarasEntity> entidades = ficofiarasRepository.findPendientes();
        return entidades.stream().map(e -> {
            PendienteArticuloResponse dto = new PendienteArticuloResponse();
            dto.setNumeroConteo(e.getARASNUCO());
            dto.setCodigoQr(e.getARASCOQR());
            dto.setCantidadContada(e.getARASCANT());
            dto.setEstado(e.getARASESTA());
            dto.setIdBodega(e.getARASIDBO());
            dto.setIdArticulo(e.getARASIDAR());
            dto.setIdUsuario(e.getARASIDUS());
            return dto;
        }).collect(Collectors.toList());
    }

    @Override
    public List<PendienteArticuloResponse> obtenerPendientesPorUsuario(Long idUsuario) {
        List<FicofiarasEntity> entidades = ficofiarasRepository.findPendientesPorUsuario(idUsuario);
        return entidades.stream().map(e -> {
            PendienteArticuloResponse dto = new PendienteArticuloResponse();
            dto.setNumeroConteo(e.getARASNUCO());
            dto.setCodigoQr(e.getARASCOQR());
            dto.setCantidadContada(e.getARASCANT());
            dto.setEstado(e.getARASESTA());
            dto.setIdBodega(e.getARASIDBO());
            dto.setIdArticulo(e.getARASIDAR());
            dto.setIdUsuario(e.getARASIDUS());
            return dto;
        }).collect(Collectors.toList());
    }
}
