package com.finte.sigapp.repository;

import com.finte.sigapp.dto.response.TransferCreatedResponse;
import com.finte.sigapp.utils.ProcedureExecutor;
import com.finte.sigapp.utils.ProcedureParam;
import jakarta.persistence.ParameterMode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Procedimientos PL/SQL del paquete PKG_FI_MOVITRAS.
 *
 * El ciclo completo del tramite (crear, aprobar/rechazar, firmar, recibir) vive
 * en PL/SQL: es ahi donde se resuelven, contra los parametros del sistema
 * (FunBuscPara), los tipos de movimiento con los que se inserta en el ERP, y
 * donde se crea el documento REQUSUMI del que cuelga el traspaso. Java solo
 * orquesta y valida lo que puede validar sin conocer esa configuracion.
 *
 * Las consultas de lectura viven en {@link TransferJpaRepository} y
 * {@link MovitrasRepository}.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class TransferRepository {

        private static final String PAQUETE = "PKG_FI_MOVITRAS";
        private static final String PARAM_ID_TRAMITE = "p_motridmt";
        private static final String PARAM_TIPO_DOCU = "p_motrdrtd";
        private static final String PARAM_NUME_DOCU = "p_motrdrnd";

        public static final String FIRMA_FUENTE = "FU";
        public static final String FIRMA_DESTINO = "DE";

        private final ProcedureExecutor procedureExecutor;

        /**
         * Invoca PKG_FI_MOVITRAS.pro_crear_solicitud, que inserta primero la
         * requisicion en REQUSUMI y despues el traspaso en FI_MOVITRAS.
         *
         * @param tipoMovimiento opcional. Si llega null el procedimiento resuelve el
         *                       tipo de documento con FunBuscPara('TIDO_TRASPASO').
         * @return id del traspaso y llave del documento generado
         */
        public TransferCreatedResponse crearSolicitud(String empresa,
                                                      String personaFuente,
                                                      String personaDestino,
                                                      String elemento,
                                                      String placa,
                                                      String observacion,
                                                      String tipoMovimiento,
                                                      String usuarioCrea) {

                log.info("Creando traspaso - Empresa: {}, Elemento: {}, Placa: {}, Persona fuente: {}, Persona destino: {}",
                          empresa, elemento, placa, personaFuente, personaDestino);

                List<ProcedureParam> params = List.of(new ProcedureParam("p_motrdrem", ParameterMode.IN, String.class, empresa),
                                                      new ProcedureParam("p_motrpefu", ParameterMode.IN, String.class, personaFuente),
                                                      new ProcedureParam("p_motrpede", ParameterMode.IN, String.class, personaDestino),
                                                      new ProcedureParam("p_motrarti", ParameterMode.IN, String.class, elemento),
                                                      new ProcedureParam("p_motrplac", ParameterMode.IN, String.class, placa),
                                                      new ProcedureParam("p_motrobse", ParameterMode.IN, String.class, observacion),
                                                      new ProcedureParam("p_tipo_movi", ParameterMode.IN, String.class, tipoMovimiento),
                                                      new ProcedureParam("p_motruscr", ParameterMode.IN, String.class, usuarioCrea),
                                                      new ProcedureParam(PARAM_ID_TRAMITE, ParameterMode.OUT, Long.class, null),
                                                      new ProcedureParam(PARAM_TIPO_DOCU, ParameterMode.OUT, String.class, null),
                                                      new ProcedureParam(PARAM_NUME_DOCU, ParameterMode.OUT, BigDecimal.class, null));

                Map<String, Object> resultado = procedureExecutor.ejecutarProcedimientoConSalida(PAQUETE + ".pro_crear_solicitud", params);

                Object idTramite = resultado.get(PARAM_ID_TRAMITE);

                if (idTramite == null) {
                        return null;
                }

                return TransferCreatedResponse.builder()
                                .id(((Number) idTramite).longValue())
                                .empresaDocumento(empresa)
                                .tipoDocumento((String) resultado.get(PARAM_TIPO_DOCU))
                                .numeroDocumento(aBigDecimal(resultado.get(PARAM_NUME_DOCU)))
                                .build();
        }

        /**
         * Invoca PKG_FI_MOVITRAS.pro_aprobar_rechazar. El procedimiento valida que el
         * traspaso este en estado 'pe' y sincroniza el estado del documento REQUSUMI.
         *
         * <p>
         * Al aprobar genera ademas el documento de inventario del ERP (cabecera en
         * DOCUINVE y su linea de detalle en MOVIINVE), todo dentro de la misma
         * transaccion. Al rechazar no se inserta nada: el documento se anula.
         *
         * @param estado nuevo estado: 'ap' (aprobado) o 'na' (rechazado)
         */
        public void aprobarRechazar(Long idTramite,
                                    String estado,
                                    String observacion,
                                    String usuarioActualiza) {

                log.info("Procesando traspaso {} con estado {}", idTramite, estado);

                List<ProcedureParam> params = List.of(new ProcedureParam(PARAM_ID_TRAMITE, ParameterMode.IN, Long.class, idTramite),
                                                      new ProcedureParam("p_estado_nvo", ParameterMode.IN, String.class, estado),
                                                      new ProcedureParam("p_observa", ParameterMode.IN, String.class, observacion),
                                                      new ProcedureParam("p_usuario", ParameterMode.IN, String.class, usuarioActualiza));

                procedureExecutor.ejecutarProcedimiento(PAQUETE + ".pro_aprobar_rechazar", params);
        }

        /**
         * Invoca PKG_FI_MOVITRAS.pro_registrar_firma, que guarda la firma en MOTRFIFU
         * o MOTRFIDE segun el tipo, sella la fecha de aceptacion correspondiente
         * (MOTRFAFU / MOTRFADE) y mueve el estado a 'af' o 'ad'.
         *
         * El procedimiento valida el estado del tramite y el orden de las firmas (el
         * destino no puede firmar antes que la fuente); Java valida el contenido de
         * la imagen.
         *
         * @param tipoFirma {@link #FIRMA_FUENTE} o {@link #FIRMA_DESTINO}
         * @param firma     bytes de la imagen, ya validada y decodificada por
         *                  {@link com.finte.sigapp.utils.FirmaUtil}. El parametro del
         *                  paquete es BLOB, y ProcedureExecutor enlaza el byte[] como
         *                  java.sql.Blob para que no aplique el tope de 32 KB del RAW.
         */
        public void registrarFirma(Long   idTramite,
                                   String tipoFirma,
                                   byte[] firma,
                                   String usuario) {

                log.info("Registrando firma {} del traspaso {} ({} bytes)",
                          tipoFirma, idTramite, firma == null ? 0 : firma.length);

                List<ProcedureParam> params = List.of(new ProcedureParam(PARAM_ID_TRAMITE, ParameterMode.IN, Long.class, idTramite),
                                                      new ProcedureParam("p_tipo_firma", ParameterMode.IN, String.class, tipoFirma),
                                                      new ProcedureParam("p_firma", ParameterMode.IN, byte[].class, firma),
                                                      new ProcedureParam("p_usuario", ParameterMode.IN, String.class, usuario));

                procedureExecutor.ejecutarProcedimiento(PAQUETE + ".pro_registrar_firma", params);
        }

        /**
         * Invoca PKG_FI_MOVITRAS.pro_recibir, que sella MOTRFERE y deja el traspaso
         * en estado 're'. Exige que ambas partes hayan firmado.
         */
        public void recibir(Long idTramite, String usuario) {

                log.info("Registrando recepcion del traspaso {}", idTramite);

                List<ProcedureParam> params = List.of(new ProcedureParam(PARAM_ID_TRAMITE, ParameterMode.IN, Long.class, idTramite),
                                                      new ProcedureParam("p_usuario", ParameterMode.IN, String.class, usuario));

                procedureExecutor.ejecutarProcedimiento(PAQUETE + ".pro_recibir", params);
        }

        /**
         * El driver puede devolver el OUT numerico como BigDecimal, Long o Integer
         * segun el tipo declarado en el procedimiento.
         */
        private BigDecimal aBigDecimal(Object valor) {
                if (valor == null) {
                        return null;
                }
                if (valor instanceof BigDecimal decimal) {
                        return decimal;
                }
                return BigDecimal.valueOf(((Number) valor).longValue());
        }
}
