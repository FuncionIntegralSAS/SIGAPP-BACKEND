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
 * El ciclo completo del tramite (crear, agregar articulos, aprobar/rechazar,
 * firmar, recibir) vive en PL/SQL: es ahi donde se resuelven, contra los
 * parametros del sistema (FunBuscPara), los tipos de movimiento con los que se
 * inserta en el ERP, y donde se crea el documento REQUSUMI del que cuelga el
 * traspaso. Java solo orquesta y valida lo que puede validar sin conocer esa
 * configuracion.
 *
 * Todos los procedimientos identifican el tramite por su NUMERO DE TRAMITE
 * (MOTRNUTR), no por MOTRIDMT, que es la clave tecnica de una linea.
 *
 * Las consultas de lectura viven en {@link TransferJpaRepository},
 * {@link MovitrasRepository} y {@link MotrfirmRepository}.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class TransferRepository {

        private static final String PAQUETE = "PKG_FI_MOVITRAS";
        private static final String PARAM_NUM_TRAMITE = "p_motrnutr";
        private static final String PARAM_TIPO_DOCU = "p_motrdrtd";
        private static final String PARAM_NUME_DOCU = "p_motrdrnd";

        public static final String FIRMA_FUENTE = "FU";
        public static final String FIRMA_DESTINO = "DE";

        private final ProcedureExecutor procedureExecutor;

        /**
         * Invoca PKG_FI_MOVITRAS.pro_crear_solicitud, que abre el traspaso con su
         * PRIMER articulo: inserta la requisicion en REQUSUMI, la primera linea de
         * FI_MOVITRAS y las dos filas de FI_MOTRFIRM con la firma vacia.
         *
         * Los articulos restantes entran con {@link #agregarArticulo}. El alta va en
         * dos procedimientos porque el documento necesita datos que salen del activo
         * (RESUCEIN) y no se puede crear antes de conocer al menos uno.
         *
         * @param tipoMovimiento opcional. Si llega null el procedimiento resuelve el
         *                       tipo de documento con FunBuscPara('TIMOTRIN').
         * @return numero de tramite y llave del documento generado
         */
        public TransferCreatedResponse crearSolicitud(String empresa,
                                                      String personaFuente,
                                                      String personaDestino,
                                                      String articulo,
                                                      String placa,
                                                      String observacion,
                                                      String tipoMovimiento,
                                                      String usuarioCrea) {

                log.info("Creando traspaso - Empresa: {}, Articulo: {}, Placa: {}, Persona fuente: {}, Persona destino: {}",
                          empresa, articulo, placa, personaFuente, personaDestino);

                List<ProcedureParam> params = List.of(new ProcedureParam("p_motrdrem", ParameterMode.IN, String.class, empresa),
                                                      new ProcedureParam("p_motrpefu", ParameterMode.IN, String.class, personaFuente),
                                                      new ProcedureParam("p_motrpede", ParameterMode.IN, String.class, personaDestino),
                                                      new ProcedureParam("p_motrarti", ParameterMode.IN, String.class, articulo),
                                                      new ProcedureParam("p_motrplac", ParameterMode.IN, String.class, placa),
                                                      new ProcedureParam("p_motrobse", ParameterMode.IN, String.class, observacion),
                                                      new ProcedureParam("p_tipo_movi", ParameterMode.IN, String.class, tipoMovimiento),
                                                      new ProcedureParam("p_motruscr", ParameterMode.IN, String.class, usuarioCrea),
                                                      new ProcedureParam(PARAM_NUM_TRAMITE, ParameterMode.OUT, Long.class, null),
                                                      new ProcedureParam(PARAM_TIPO_DOCU, ParameterMode.OUT, String.class, null),
                                                      new ProcedureParam(PARAM_NUME_DOCU, ParameterMode.OUT, BigDecimal.class, null));

                Map<String, Object> resultado = procedureExecutor.ejecutarProcedimientoConSalida(PAQUETE + ".pro_crear_solicitud", params);

                Object numeroTramite = resultado.get(PARAM_NUM_TRAMITE);

                if (numeroTramite == null) {
                        return null;
                }

                return TransferCreatedResponse.builder()
                                .id(((Number) numeroTramite).longValue())
                                .empresaDocumento(empresa)
                                .tipoDocumento((String) resultado.get(PARAM_TIPO_DOCU))
                                .numeroDocumento(aBigDecimal(resultado.get(PARAM_NUME_DOCU)))
                                .build();
        }

        /**
         * Invoca PKG_FI_MOVITRAS.pro_agregar_articulo, que anade una linea al tramite
         * copiando de la existente todos los datos comunes.
         *
         * El procedimiento exige que el tramite siga en 'pe' y rechaza el articulo
         * repetido o el que no comparta centro de informacion y tercero con el resto,
         * porque esos dos son datos de cabecera del documento.
         */
        public void agregarArticulo(Long   numeroTramite,
                                    String articulo,
                                    String placa,
                                    String usuario) {

                log.info("Agregando el articulo {} placa {} al traspaso {}", articulo, placa, numeroTramite);

                List<ProcedureParam> params = List.of(new ProcedureParam(PARAM_NUM_TRAMITE, ParameterMode.IN, Long.class, numeroTramite),
                                                      new ProcedureParam("p_motrarti", ParameterMode.IN, String.class, articulo),
                                                      new ProcedureParam("p_motrplac", ParameterMode.IN, String.class, placa),
                                                      new ProcedureParam("p_usuario", ParameterMode.IN, String.class, usuario));

                procedureExecutor.ejecutarProcedimiento(PAQUETE + ".pro_agregar_articulo", params);
        }

        /**
         * Invoca PKG_FI_MOVITRAS.pro_aprobar_rechazar. El procedimiento valida que el
         * traspaso este en estado 'pe' y aplica el cambio a TODAS sus lineas: el
         * tramite se aprueba o se rechaza completo, nunca por articulo.
         *
         * <p>
         * NO genera el documento de inventario. Aprobar autoriza el tramite; DOCUINVE
         * y MOVIINVE los crea {@link #recibir}, cuando las dos partes ya firmaron.
         *
         * @param estado nuevo estado: 'ap' (aprobado) o 'na' (rechazado)
         */
        public void aprobarRechazar(Long numeroTramite,
                                    String estado,
                                    String observacion,
                                    String usuarioActualiza) {

                log.info("Procesando traspaso {} con estado {}", numeroTramite, estado);

                List<ProcedureParam> params = List.of(new ProcedureParam(PARAM_NUM_TRAMITE, ParameterMode.IN, Long.class, numeroTramite),
                                                      new ProcedureParam("p_estado_nvo", ParameterMode.IN, String.class, estado),
                                                      new ProcedureParam("p_observa", ParameterMode.IN, String.class, observacion),
                                                      new ProcedureParam("p_usuario", ParameterMode.IN, String.class, usuarioActualiza));

                procedureExecutor.ejecutarProcedimiento(PAQUETE + ".pro_aprobar_rechazar", params);
        }

        /**
         * Invoca PKG_FI_MOVITRAS.pro_registrar_firma, que guarda la firma en la fila
         * de FI_MOTRFIRM correspondiente a la posicion y sella su fecha.
         *
         * No hay orden entre las dos firmas ni se toca el estado del tramite: eso lo
         * decide el paquete. Aqui solo se entrega la imagen ya validada.
         *
         * @param tipoFirma {@link #FIRMA_FUENTE} o {@link #FIRMA_DESTINO}
         * @param firma     bytes de la imagen, ya validada y decodificada por
         *                  {@link com.finte.sigapp.utils.FirmaUtil}. El parametro del
         *                  paquete es BLOB, y ProcedureExecutor enlaza el byte[] como
         *                  java.sql.Blob para que no aplique el tope de 32 KB del RAW.
         */
        public void registrarFirma(Long   numeroTramite,
                                   String tipoFirma,
                                   byte[] firma,
                                   String usuario) {

                log.info("Registrando firma {} del traspaso {} ({} bytes)",
                          tipoFirma, numeroTramite, firma == null ? 0 : firma.length);

                List<ProcedureParam> params = List.of(new ProcedureParam(PARAM_NUM_TRAMITE, ParameterMode.IN, Long.class, numeroTramite),
                                                      new ProcedureParam("p_tipo_firma", ParameterMode.IN, String.class, tipoFirma),
                                                      new ProcedureParam("p_firma", ParameterMode.IN, byte[].class, firma),
                                                      new ProcedureParam("p_usuario", ParameterMode.IN, String.class, usuario));

                procedureExecutor.ejecutarProcedimiento(PAQUETE + ".pro_registrar_firma", params);
        }

        /**
         * Invoca PKG_FI_MOVITRAS.pro_recibir, que exige las dos firmas, genera el
         * documento de inventario (una cabecera en DOCUINVE y una linea por articulo
         * en MOVIINVE), sella MOTRFERE y deja el traspaso en 're'.
         *
         * Es aqui y no en la aprobacion donde el traspaso llega al inventario: el
         * movimiento dice que el elemento cambio de manos, y eso solo es cierto con
         * las dos firmas.
         */
        public void recibir(Long numeroTramite, String usuario) {

                log.info("Registrando recepcion del traspaso {}", numeroTramite);

                List<ProcedureParam> params = List.of(new ProcedureParam(PARAM_NUM_TRAMITE, ParameterMode.IN, Long.class, numeroTramite),
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
