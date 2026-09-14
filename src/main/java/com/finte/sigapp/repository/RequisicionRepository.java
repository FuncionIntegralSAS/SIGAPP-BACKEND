package com.finte.sigapp.repository;

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
 * Escritura del modulo de requisiciones: llamadas a PKG_FI_REQUISICION.
 *
 * Toda la escritura pasa por aqui y ninguna por JPA. Los UPDATE sobre MOVIRESU
 * disparan un trigger del ERP que valida disponibilidad, placas y saldos y
 * propaga a ARTIBODE y SALDINLA; hacerlos desde Hibernate saltaria ademas el
 * bloqueo que el paquete toma sobre la requisicion.
 *
 * ORDEN OBLIGATORIO: {@link #fijarContexto(String)} va primero en toda
 * transaccion que escriba. Sin eso el control de responsable de bodega del ERP
 * no puede identificar al usuario y deja pasar la operacion sin avisar.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class RequisicionRepository {

    private static final String PAQUETE = "PKG_FI_REQUISICION";

    private static final String PARAM_EMPRESA  = "p_morstdem";
    private static final String PARAM_TIPODOCU = "p_morstido";
    private static final String PARAM_NUMERO   = "p_morsrenu";
    private static final String PARAM_ARTICULO = "p_morsarti";
    private static final String PARAM_BODEGA   = "p_morsbode";
    private static final String PARAM_SECUENCIA = "p_morssecu";
    private static final String PARAM_CANTIDAD = "p_cantidad";
    private static final String PARAM_USUARIO  = "p_usuario";
    private static final String PARAM_PLACA    = "p_placa";

    /** Nombre del parametro OUT con el tipo de documento de salida que se uso. */
    public static final String SALIDA_TIPO_DOCUMENTO = "p_tidosali";

    /** Nombre del parametro OUT con el mensaje que devuelve el ERP. */
    public static final String SALIDA_MENSAJE = "p_mensaje";

    private final ProcedureExecutor procedureExecutor;

    /**
     * Fija la identidad del usuario en la sesion Oracle.
     *
     * No es opcional. El parametro CONREBEQ del ERP esta activo, asi que el
     * trigger de MOVIRESU comprueba que quien entrega o registra sea responsable
     * de la bodega, y para saberlo lee una variable de paquete de la sesion. Con
     * un pool de conexiones esa variable no se establece sola y una conexion
     * reciclada conserva el valor del usuario anterior.
     */
    public void fijarContexto(String usuario) {
        log.debug("Fijando contexto de sesion Oracle para el usuario {}", usuario);

        procedureExecutor.ejecutarProcedimiento(PAQUETE + ".pro_set_contexto",
                List.of(new ProcedureParam(PARAM_USUARIO, ParameterMode.IN, String.class, usuario)));
    }

    /** Aprueba una linea con la cantidad indicada: in -> ap. */
    public void aprobarLinea(String empresa, String tipoDocumento, Long numero,
                             String articulo, String bodega, BigDecimal secuencia,
                             BigDecimal cantidad, String usuario) {

        procedureExecutor.ejecutarProcedimiento(PAQUETE + ".pro_aprobar_linea",
                parametrosDeLinea(empresa, tipoDocumento, numero, articulo, bodega, secuencia, cantidad, usuario));
    }

    /** Entrega una linea con la cantidad indicada: ap -> en. */
    public void entregarLinea(String empresa, String tipoDocumento, Long numero,
                              String articulo, String bodega, BigDecimal secuencia,
                              BigDecimal cantidad, String usuario) {

        procedureExecutor.ejecutarProcedimiento(PAQUETE + ".pro_entregar_linea",
                parametrosDeLinea(empresa, tipoDocumento, numero, articulo, bodega, secuencia, cantidad, usuario));
    }

    /** Sella la cantidad recibida de una linea, que puede ser menor que la entregada. */
    public void recibirLinea(String empresa, String tipoDocumento, Long numero,
                             String articulo, String bodega, BigDecimal secuencia,
                             BigDecimal cantidad, String usuario) {

        procedureExecutor.ejecutarProcedimiento(PAQUETE + ".pro_recibir_linea",
                parametrosDeLinea(empresa, tipoDocumento, numero, articulo, bodega, secuencia, cantidad, usuario));
    }

    /** Anula una linea al entregar: ap -> ae. Sin cantidad: anula el saldo aprobado. */
    public void anularLinea(String empresa, String tipoDocumento, Long numero,
                            String articulo, String bodega, BigDecimal secuencia, String usuario) {

        procedureExecutor.ejecutarProcedimiento(PAQUETE + ".pro_anular_linea",
                List.of(new ProcedureParam(PARAM_EMPRESA, ParameterMode.IN, String.class, empresa),
                        new ProcedureParam(PARAM_TIPODOCU, ParameterMode.IN, String.class, tipoDocumento),
                        new ProcedureParam(PARAM_NUMERO, ParameterMode.IN, Long.class, numero),
                        new ProcedureParam(PARAM_ARTICULO, ParameterMode.IN, String.class, articulo),
                        new ProcedureParam(PARAM_BODEGA, ParameterMode.IN, String.class, bodega),
                        new ProcedureParam(PARAM_SECUENCIA, ParameterMode.IN, BigDecimal.class, secuencia),
                        new ProcedureParam(PARAM_USUARIO, ParameterMode.IN, String.class, usuario)));
    }

    /** Asigna una placa a una linea devolutiva. Repetirla no es error. */
    public void asignarPlaca(String empresa, String tipoDocumento, Long numero,
                             String articulo, BigDecimal secuencia, String placa) {

        procedureExecutor.ejecutarProcedimiento(PAQUETE + ".pro_asignar_placa",
                parametrosDePlaca(empresa, tipoDocumento, numero, articulo, secuencia, placa));
    }

    /** Suelta una placa que no se recibe, para la recepcion parcial de un devolutivo. */
    public void soltarPlaca(String empresa, String tipoDocumento, Long numero,
                            String articulo, BigDecimal secuencia, String placa) {

        procedureExecutor.ejecutarProcedimiento(PAQUETE + ".pro_soltar_placa",
                parametrosDePlaca(empresa, tipoDocumento, numero, articulo, secuencia, placa));
    }

    /**
     * Registra una de las dos firmas.
     *
     * La firma viaja como {@code byte[]}: ProcedureExecutor la envuelve en un
     * java.sql.Blob antes de enlazarla. Si se pasara como RAW el driver topa en
     * 32 KB y falla con ORA-01460, que es justo lo que una firma manuscrita
     * supera con facilidad. Por eso exige transaccion abierta.
     */
    public void registrarFirma(String empresa, String tipoDocumento, Long numero,
                               String tipoFirma, String persona, byte[] firma, String usuario) {

        procedureExecutor.ejecutarProcedimiento(PAQUETE + ".pro_registrar_firma",
                List.of(new ProcedureParam(PARAM_EMPRESA, ParameterMode.IN, String.class, empresa),
                        new ProcedureParam(PARAM_TIPODOCU, ParameterMode.IN, String.class, tipoDocumento),
                        new ProcedureParam(PARAM_NUMERO, ParameterMode.IN, Long.class, numero),
                        new ProcedureParam("p_tipo_firma", ParameterMode.IN, String.class, tipoFirma),
                        new ProcedureParam("p_persona", ParameterMode.IN, String.class, persona),
                        new ProcedureParam("p_firma", ParameterMode.IN, byte[].class, firma),
                        new ProcedureParam(PARAM_USUARIO, ParameterMode.IN, String.class, usuario)));
    }

    /**
     * Genera la salida de inventario y deja la requisicion en 'rg'.
     *
     * Punto de no retorno: crea el documento en DOCUINVE con sus lineas en
     * MOVIINVE y eso no se deshace desde la aplicacion. El paquete es
     * reentrante, asi que repetir la llamada sobre una requisicion ya registrada
     * devuelve el aviso en el mensaje y no genera un segundo documento.
     *
     * @return los dos parametros de salida: {@link #SALIDA_TIPO_DOCUMENTO} y
     *         {@link #SALIDA_MENSAJE}
     */
    public Map<String, Object> generarSalida(String empresa, String tipoDocumento, Long numero, String usuario) {

        return procedureExecutor.ejecutarProcedimientoConSalida(PAQUETE + ".pro_generar_salida",
                List.of(new ProcedureParam(PARAM_EMPRESA, ParameterMode.IN, String.class, empresa),
                        new ProcedureParam(PARAM_TIPODOCU, ParameterMode.IN, String.class, tipoDocumento),
                        new ProcedureParam(PARAM_NUMERO, ParameterMode.IN, Long.class, numero),
                        new ProcedureParam(PARAM_USUARIO, ParameterMode.IN, String.class, usuario),
                        new ProcedureParam(SALIDA_TIPO_DOCUMENTO, ParameterMode.OUT, String.class, null),
                        new ProcedureParam(SALIDA_MENSAJE, ParameterMode.OUT, String.class, null)));
    }

    /**
     * Aprobar, entregar y recibir reciben exactamente los mismos parametros: la
     * linea, una cantidad y el usuario. Lo que cambia es que valida cada uno.
     */
    private List<ProcedureParam> parametrosDeLinea(String empresa, String tipoDocumento, Long numero,
                                                   String articulo, String bodega, BigDecimal secuencia,
                                                   BigDecimal cantidad, String usuario) {

        return List.of(new ProcedureParam(PARAM_EMPRESA, ParameterMode.IN, String.class, empresa),
                new ProcedureParam(PARAM_TIPODOCU, ParameterMode.IN, String.class, tipoDocumento),
                new ProcedureParam(PARAM_NUMERO, ParameterMode.IN, Long.class, numero),
                new ProcedureParam(PARAM_ARTICULO, ParameterMode.IN, String.class, articulo),
                new ProcedureParam(PARAM_BODEGA, ParameterMode.IN, String.class, bodega),
                new ProcedureParam(PARAM_SECUENCIA, ParameterMode.IN, BigDecimal.class, secuencia),
                new ProcedureParam(PARAM_CANTIDAD, ParameterMode.IN, BigDecimal.class, cantidad),
                new ProcedureParam(PARAM_USUARIO, ParameterMode.IN, String.class, usuario));
    }

    /** ASPLMORS no lleva bodega: la placa cuelga de articulo y secuencia. */
    private List<ProcedureParam> parametrosDePlaca(String empresa, String tipoDocumento, Long numero,
                                                   String articulo, BigDecimal secuencia, String placa) {

        return List.of(new ProcedureParam(PARAM_EMPRESA, ParameterMode.IN, String.class, empresa),
                new ProcedureParam(PARAM_TIPODOCU, ParameterMode.IN, String.class, tipoDocumento),
                new ProcedureParam(PARAM_NUMERO, ParameterMode.IN, Long.class, numero),
                new ProcedureParam(PARAM_ARTICULO, ParameterMode.IN, String.class, articulo),
                new ProcedureParam(PARAM_SECUENCIA, ParameterMode.IN, BigDecimal.class, secuencia),
                new ProcedureParam(PARAM_PLACA, ParameterMode.IN, String.class, placa));
    }
}
