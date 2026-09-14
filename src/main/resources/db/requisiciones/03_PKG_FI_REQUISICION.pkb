create or replace package body pkg_fi_requisicion is

   -----------------------------------------------------------------------------
   -- COMPILAR EN EL ESQUEMA DE RECURSOS FISICOS SRF (SRFPNACUA), dueno de
   -- FI_REQUFIRM y del que dependen sin calificar las tablas del ERP que se usan
   -- aqui (REQUSUMI, MOVIRESU, ASPLMORS, EQUIVALE, PARAOPER, ARTICULO) y el
   -- procedimiento PRO_GENERA_SALIREQU.
   --
   -- INVARIANTES DEL MODULO
   --
   -- 1. La cabecera no se toca. REQUSUMI.RESUESTA se queda en 'ac'. El estado de
   --    la requisicion es una funcion de sus lineas (fun_estado), no una
   --    columna. Escribirlo seria inventar semantica que el ERP no tiene.
   --
   -- 2. Toda escritura empieza por pro_bloquea_requi, que bloquea PRIMERO la
   --    fila de REQUSUMI y despues las N lineas. Bloquear la cabecera convierte
   --    esa fila en un mutex de toda la requisicion: dos sesiones no pueden
   --    entrelazarse a mitad de las lineas y no hay orden de bloqueo que
   --    produzca deadlock.
   --
   -- 3. El trigger TRGBUPR_MOVIRESU hace el trabajo pesado en cada UPDATE:
   --    valida cantidades, disponibilidad y placas, y propaga a ARTIBODE,
   --    SALDINLA y REQUSUMI. Este paquete no lo replica. Si un UPDATE parece
   --    demasiado simple, es por eso.
   --
   -- 4. MORSCARE se sella ANTES de llamar al ERP, nunca despues:
   --    PRO_GENERA_SALIREQU la lee como la cantidad que lleva a MOVIINVE y si
   --    vale cero no genera movimiento para esa linea, sin avisar.
   -----------------------------------------------------------------------------

   -----------------------------------------------------------------------------
   -- Claves del maestro de parametros del sistema (PARAOPER, se leen con
   -- FunBuscPara sobre PARACODI y devuelven PARACOVA).
   --
   -- Todas existen ya en el ERP. Este modulo NO da de alta parametros nuevos.
   -----------------------------------------------------------------------------
   c_estadoaprob   varchar2(10) := 'ESTAAPRO'; -- 'ap' aprobado
   c_estadoentr    varchar2(10) := 'ESTAENTR'; -- 'en' entregado
   c_estadoregi    varchar2(10) := 'ESTAREGI'; -- 'rg' registrado
   c_estadoactivo  varchar2(10) := 'ESTAACTI'; -- 'ac' activo
   c_estadoanuen   varchar2(10) := 'ESTAANEN'; -- 'ae' anulado al entregar
   c_estadoinic    varchar2(10) := 'ESTAINIC'; -- 'in' inicial
   c_articonsumo   varchar2(10) := 'ARTICONS'; -- clase de los articulos de consumo

   -----------------------------------------------------------------------------
   -- Equivalencia que resuelve el tipo de documento de SALIDA a partir del tipo
   -- de la requisicion.
   --
   -- Van como constantes y no como filas nuevas en PARAOPER porque este modulo
   -- no da de alta parametros. Si algun dia hay que parametrizarlas, se pide.
   -----------------------------------------------------------------------------
   c_claseequiv    varchar2(4) := 'COBO';  -- EQUICLEQ
   c_tipoequiv     varchar2(4) := 'REQU';  -- EQUITIPO

   -- Numeracion automatica del documento de salida (parametro sbTipoNume de
   -- PRO_GENERA_SALIREQU). 'A' hace que el ERP tome el proximo del numerador.
   c_numeauto      varchar2(1) := 'A';

   -- Posiciones de la firma en FI_REQUFIRM
   c_posisalida    number := 1;   -- quien entrega
   c_posirecibe    number := 2;   -- quien recibe

   -- Longitud de MORSUSAP. No se trunca con SUBSTR: una columna de auditoria
   -- que miente es peor que un error.
   c_maxusuario    number := 12;

   -- Objeto con el que se identifican los errores del modulo en el log del ERP.
   c_tabla         varchar2(30) := 'FI_REQUFIRM';


   -----------------------------------------------------------------------------
   -- pro_log
   -----------------------------------------------------------------------------
   procedure pro_log (p_mensaje in varchar2)
   is
   begin
      pkgerrointe.procrear(c_tabla, user, systimestamp || ' ' || p_mensaje);
   exception
      when others then
         null;
   end pro_log;

   -----------------------------------------------------------------------------
   -- pro_error
   --
   -- Error de negocio. Deja rastro en el log del ERP y arma el mensaje que
   -- viajara al llamador. Las dos cosas, que no son intercambiables: el log
   -- sobrevive al rollback y el mensaje llega al usuario.
   -----------------------------------------------------------------------------
   procedure pro_error (p_mensaje in varchar2)
   is
   begin
      pro_log(p_mensaje);
      pkgerror.proiniterrtxt(p_mensaje);
   end pro_error;

   -----------------------------------------------------------------------------
   -- fun_parametro
   -----------------------------------------------------------------------------
   function fun_parametro (p_clave in varchar2)
   return varchar2 is
      sb_valor varchar2(100);
   begin
      sb_valor := funbuscpara(p_clave);

      if (sb_valor is null) then
         pro_error('El parametro [' || p_clave || '] no esta configurado en PARAOPER');
         raise pkgerror.generic;
      end if;

      return sb_valor;
   end fun_parametro;

   -----------------------------------------------------------------------------
   -- fun_hora / fun_minuto
   --
   -- MOVIRESU guarda el sello de tiempo en tres columnas: la fecha como DATE y
   -- la hora y el minuto por separado. La fecha se escribe siempre con
   -- trunc(sysdate): si la hora vive aparte, dejarla dentro del DATE duplica el
   -- dato y hace que dos filas "de la misma hora" comparen distinto.
   --
   -- El tipo de retorno va anclado con %TYPE a proposito: asi la conversion la
   -- resuelve el RETURN y la funcion sirve igual tanto si la columna es NUMBER
   -- como si fuera VARCHAR2 con cero a la izquierda.
   -----------------------------------------------------------------------------
   function fun_hora return moviresu.morshoap%type is
   begin
      return to_number(to_char(sysdate, 'HH24'));
   end fun_hora;

   function fun_minuto return moviresu.morsmiap%type is
   begin
      return to_number(to_char(sysdate, 'MI'));
   end fun_minuto;

   -----------------------------------------------------------------------------
   -- pro_valida_usuario
   --
   -- MORSUSAP es VARCHAR2(12) y el codigo que llega del token puede ser mas
   -- largo. Falla explicitamente en vez de truncar.
   -----------------------------------------------------------------------------
   procedure pro_valida_usuario (p_usuario in varchar2)
   is
   begin
      if (p_usuario is null) then
         pro_error('No se recibio el usuario que ejecuta la operacion');
         raise pkgerror.generic;
      end if;

      if (length(p_usuario) > c_maxusuario) then
         pro_error('El usuario [' || p_usuario || '] excede los ' || c_maxusuario ||
                   ' caracteres de la columna de aprobador del ERP');
         raise pkgerror.generic;
      end if;
   end pro_valida_usuario;

   -----------------------------------------------------------------------------
   -- pro_bloquea_requi
   --
   -- Bloquea la requisicion entera y devuelve cuantas lineas tiene.
   --
   -- Sobre la cabecera SI se puede hacer SELECT INTO: REQUSUMI tiene una sola
   -- fila por terna. Sobre las lineas NO, serian N y daria TOO_MANY_ROWS; por
   -- eso se recorren con un cursor FOR UPDATE, que toma el lock de todas.
   --
   -- El ORDER BY fija un orden de toma de bloqueos identico en todas las
   -- sesiones, que es lo que evita el deadlock entre dos usuarios simultaneos.
   -----------------------------------------------------------------------------
   procedure pro_bloquea_requi (p_morstdem in  moviresu.morstdem%type,
                                p_morstido in  moviresu.morstido%type,
                                p_morsrenu in  moviresu.morsrenu%type,
                                rg_cabecera out requsumi%rowtype,
                                p_lineas   out number)
   is
   begin
      p_lineas := 0;

      begin
         select *
           into rg_cabecera
           from requsumi
          where resutdem = p_morstdem
            and resutido = p_morstido
            and resunume = p_morsrenu
            for update;
      exception
         when no_data_found then
            pro_error('No existe la requisicion [' || p_morstdem || '-' ||
                      p_morstido || '-' || p_morsrenu || ']');
            raise pkgerror.generic;
      end;

      for rg_linea in (select morsarti
                         from moviresu
                        where morstdem = p_morstdem
                          and morstido = p_morstido
                          and morsrenu = p_morsrenu
                        order by morsarti, morsbode, morssecu
                          for update)
      loop
         p_lineas := p_lineas + 1;
      end loop;

      if (p_lineas = 0) then
         pro_error('La requisicion [' || p_morstdem || '-' || p_morstido || '-' ||
                   p_morsrenu || '] no tiene movimientos');
         raise pkgerror.generic;
      end if;
   end pro_bloquea_requi;

   -----------------------------------------------------------------------------
   -- fun_bloquea_linea
   --
   -- Dentro de una requisicion, lo que identifica una linea es el trio
   -- (articulo, bodega, secuencia): la PK de MOVIRESU son seis columnas y el
   -- mismo articulo puede repetirse en otra bodega o secuencia.
   -----------------------------------------------------------------------------
   function fun_bloquea_linea (p_morstdem in moviresu.morstdem%type,
                               p_morstido in moviresu.morstido%type,
                               p_morsrenu in moviresu.morsrenu%type,
                               p_morsarti in moviresu.morsarti%type,
                               p_morsbode in moviresu.morsbode%type,
                               p_morssecu in moviresu.morssecu%type)
   return moviresu%rowtype
   is
      rg_linea moviresu%rowtype;
   begin
      select *
        into rg_linea
        from moviresu
       where morstdem = p_morstdem
         and morstido = p_morstido
         and morsrenu = p_morsrenu
         and morsarti = p_morsarti
         and morsbode = p_morsbode
         and morssecu = p_morssecu
         for update;

      return rg_linea;
   exception
      when no_data_found then
         pro_error('La linea [' || p_morsarti || '/' || p_morsbode || '/' || p_morssecu ||
                   '] no existe en la requisicion [' || p_morstdem || '-' || p_morstido ||
                   '-' || p_morsrenu || ']');
         raise pkgerror.generic;
   end fun_bloquea_linea;

   -----------------------------------------------------------------------------
   -- fun_estado
   --
   -- Estado derivado de la requisicion a partir de sus lineas. Precedencia: si
   -- queda algo por hacer en una fase temprana, ese es el estado del conjunto.
   --
   -- Esta misma precedencia esta replicada en la capa Java para pintar la
   -- pantalla, pero la que AUTORIZA es esta. Si divergieran, el peor efecto es
   -- una pantalla optimista y una accion rechazada con mensaje claro, nunca una
   -- escritura incorrecta.
   -----------------------------------------------------------------------------
   function fun_estado (p_morstdem in moviresu.morstdem%type,
                        p_morstido in moviresu.morstido%type,
                        p_morsrenu in moviresu.morsrenu%type)
   return varchar2
   is
      sb_inicial  varchar2(2) := fun_parametro(c_estadoinic);
      sb_aprobado varchar2(2) := fun_parametro(c_estadoaprob);
      sb_entrega  varchar2(2) := fun_parametro(c_estadoentr);
      sb_registra varchar2(2) := fun_parametro(c_estadoregi);
      sb_anulaen  varchar2(2) := fun_parametro(c_estadoanuen);
      in_orden    number;
   begin
      -- Una sola pasada: se traduce cada estado a su orden de precedencia y se
      -- toma el menor. Lo que quede mas atras en el ciclo manda.
      select min(case morsesta
                    when sb_inicial  then 1
                    when sb_aprobado then 2
                    when sb_entrega  then 3
                    when sb_registra then 4
                    else 5
                 end)
        into in_orden
        from moviresu
       where morstdem = p_morstdem
         and morstido = p_morstido
         and morsrenu = p_morsrenu;

      return case in_orden
                when 1 then sb_inicial
                when 2 then sb_aprobado
                when 3 then sb_entrega
                when 4 then sb_registra
                else sb_anulaen
             end;
   end fun_estado;

   -----------------------------------------------------------------------------
   -- fun_tipo_salida
   --
   -- El tipo de documento de la salida sale de la tabla de equivalencias, no de
   -- una lista en el codigo. Eso hace que el conjunto de tipos gestionables sea
   -- un dato: una requisicion cuyo tipo no este dado de alta simplemente no se
   -- puede procesar, que es como quedan fuera las de traslado.
   -----------------------------------------------------------------------------
   function fun_tipo_salida (p_morstido in moviresu.morstido%type)
   return varchar2
   is
      sb_tidosali equivale.equicoeq%type;
      -- El valor se resuelve ANTES del SELECT: una funcion del paquete no se
      -- puede invocar dentro de una sentencia SQL (PLS-00231).
      sb_activo   varchar2(2) := fun_parametro(c_estadoactivo);
   begin
      select equicoeq
        into sb_tidosali
        from equivale
       where equicleq = c_claseequiv
         and equitipo = c_tipoequiv
         and equicodi = p_morstido
         and equiesta = sb_activo;

      return sb_tidosali;
   exception
      when no_data_found then
         pro_error('El tipo de requisicion [' || p_morstido || '] no tiene equivalencia ' ||
                   c_claseequiv || '/' || c_tipoequiv || ' activa en EQUIVALE: no esta ' ||
                   'habilitado para generar salida. Solicitar el alta de la fila ' ||
                   '(EQUICLEQ=' || c_claseequiv || ', EQUITIPO=' || c_tipoequiv ||
                   ', EQUICODI=' || p_morstido || ')');
         raise pkgerror.generic;
      when too_many_rows then
         pro_error('El tipo de requisicion [' || p_morstido || '] tiene mas de una ' ||
                   'equivalencia activa en EQUIVALE: no se puede decidir el tipo de salida');
         raise pkgerror.generic;
   end fun_tipo_salida;

   -----------------------------------------------------------------------------
   -- pro_asignar_placas_fifo
   --
   -- Elige y asigna las placas de una linea devolutiva por ORDEN DE ENTRADA:
   -- lo primero que entro al almacen es lo primero que sale. Asi quien despacha
   -- recibe la lista ya resuelta en vez de tener que elegir, y la rotacion del
   -- inventario deja de depender de que cada quien se acuerde.
   --
   -- Solo actua sobre lo que falta. Si el almacenista ya asigno placas a mano
   -- -porque la mas antigua esta danada o no aparece en el estante- esas se
   -- respetan y aqui solo se completa la diferencia.
   --
   -- Un articulo de consumo no lleva placa y sale por la primera condicion. La
   -- clasificacion la decide el ERP con funValiClas sobre ARTICULO.ARTICLAS; no
   -- se replica aqui porque duplicarla la haria divergir.
   --
   -- El orden de desempate es la placa: en la practica los activos entran por
   -- lotes con la misma fecha, asi que sin un segundo criterio el resultado
   -- seria arbitrario entre ejecuciones.
   -----------------------------------------------------------------------------
   procedure pro_asignar_placas_fifo (p_morstdem in moviresu.morstdem%type,
                                      p_morstido in moviresu.morstido%type,
                                      p_morsrenu in moviresu.morsrenu%type,
                                      p_morsarti in moviresu.morsarti%type,
                                      p_morsbode in moviresu.morsbode%type,
                                      p_morssecu in moviresu.morssecu%type,
                                      p_cantidad in moviresu.morscaen%type)
   is
      rg_articulo articulo%rowtype;
      in_asignadas number;
      in_faltan    number;
      in_puestas   number := 0;
      sb_consumo   varchar2(10) := fun_parametro(c_articonsumo);
      sb_activo    varchar2(2)  := fun_parametro(c_estadoactivo);
      sb_inicial   varchar2(2)  := fun_parametro(c_estadoinic);
      sb_aprobado  varchar2(2)  := fun_parametro(c_estadoaprob);
      sb_entrega   varchar2(2)  := fun_parametro(c_estadoentr);
   begin
      pkgtbarticulo.prosel(p_morsarti, null, rg_articulo);

      if (funvaliclas(rg_articulo.articlas, sb_consumo)) then
         return;  -- de consumo: no lleva placa
      end if;

      select count(1)
        into in_asignadas
        from asplmors
       where aspltdem = p_morstdem
         and aspltido = p_morstido
         and asplnume = p_morsrenu
         and asplsecu = p_morssecu
         and asplarti = p_morsarti;

      in_faltan := p_cantidad - in_asignadas;

      if (in_faltan <= 0) then
         return;  -- ya estan todas, o el almacenista asigno de mas y eso lo corta el trigger
      end if;

      -- Candidatas: el activo sigue en la bodega FUENTE hasta que se registra la
      -- salida, momento en que el ERP lo pasa a la bodega destino. Por eso basta
      -- con filtrar por MORSBODE.
      --
      -- El NOT EXISTS evita que dos requisiciones abiertas a la vez se lleven la
      -- misma placa. Una requisicion ya registrada o anulada no bloquea nada: su
      -- activo ya cambio de bodega y el filtro anterior lo descarta solo.
      for rg_placa in (select f.acfiplac
                         from actifijo f
                        where f.acfiarti = p_morsarti
                          and f.acfibode = p_morsbode
                          and f.acfidiem = p_morstdem
                          and f.acfiesta = sb_activo
                          and not exists (select 1
                                            from asplmors a
                                            join moviresu m
                                              on m.morstdem = a.aspltdem
                                             and m.morstido = a.aspltido
                                             and m.morsrenu = a.asplnume
                                             and m.morsarti = a.asplarti
                                             and m.morssecu = a.asplsecu
                                           where a.asplarti = f.acfiarti
                                             and a.asplplac = f.acfiplac
                                             and m.morsesta in (sb_inicial, sb_aprobado, sb_entrega))
                        order by f.acfifeen, f.acfiplac
                        fetch first in_faltan rows only)
      loop
         insert into asplmors (aspltdem, aspltido, asplnume, asplsecu, asplarti, asplplac)
         values (p_morstdem, p_morstido, p_morsrenu, p_morssecu, p_morsarti, rg_placa.acfiplac);

         in_puestas := in_puestas + 1;
      end loop;

      -- Que falten placas no es un caso de negocio: el ERP ya valido la
      -- disponibilidad contra ARTIBODE antes de llegar aqui. Si se da, hay un
      -- desajuste entre el saldo de la bodega y los activos, y el mensaje lo
      -- dice para que se corrija en vez de quedar en un error opaco del trigger.
      if (in_puestas < in_faltan) then
         pro_error('Inconsistencia de inventario: el articulo [' || p_morsarti ||
                   '] necesita ' || in_faltan || ' placa(s) libre(s) en la bodega [' ||
                   p_morsbode || '] y solo hay ' || in_puestas ||
                   '. El saldo de ARTIBODE no cuadra con los activos de ACTIFIJO');
         raise pkgerror.generic;
      end if;
   end pro_asignar_placas_fifo;

   -----------------------------------------------------------------------------
   -- pro_abrir_firmas
   --
   -- Crea las dos filas vacias de la requisicion. Se llama cuando la primera
   -- linea llega a entregado. Es idempotente: la segunda linea que entregue
   -- choca con la PK y no pasa nada.
   -----------------------------------------------------------------------------
   procedure pro_abrir_firmas (p_morstdem in moviresu.morstdem%type,
                               p_morstido in moviresu.morstido%type,
                               p_morsrenu in moviresu.morsrenu%type,
                               p_usuario  in varchar2)
   is
   begin
      for in_posicion in c_posisalida .. c_posirecibe loop
         begin
            insert into fi_requfirm (refiempr, refitido, refinume, refiposi, reficrby)
            values (p_morstdem, p_morstido, p_morsrenu, in_posicion, p_usuario);
         exception
            when dup_val_on_index then
               null;
         end;
      end loop;
   end pro_abrir_firmas;

   -----------------------------------------------------------------------------
   -- fun_posicion_firma
   -----------------------------------------------------------------------------
   function fun_posicion_firma (p_tipo_firma in varchar2)
   return number
   is
   begin
      if (p_tipo_firma = c_firma_salida) then
         return c_posisalida;
      elsif (p_tipo_firma = c_firma_recibe) then
         return c_posirecibe;
      end if;

      pro_error('Tipo de firma [' || p_tipo_firma || '] invalido. Se espera ' ||
                c_firma_salida || ' (salida) o ' || c_firma_recibe || ' (recibe)');
      raise pkgerror.generic;
   end fun_posicion_firma;


   -----------------------------------------------------------------------------
   -- pro_set_contexto
   --
   -- Sin esto el trigger TRGBUPR_MOVIRESU no puede identificar a nadie y el
   -- control de responsable de bodega se salta en silencio. Ver la cabecera de
   -- la especificacion.
   -----------------------------------------------------------------------------
   procedure pro_set_contexto (p_usuario  in varchar2,
                               p_terminal in varchar2 default 'SIGAPP')
   is
   begin
      pkgerror.proseterror('PKG_FI_REQUISICION.pro_set_contexto');

      pro_valida_usuario(p_usuario);

      pkgfnpersonal.setusuario(p_usuario);
      pkgfnpersonal.setterminal(p_terminal);

      pkgerror.prounseterror;
   exception
      when pkgerror.generic then
         pkgerror.prounseterror;
         if (pkgerror.funnivelerror = 0) then
            raise_application_error(-20008, pkgerror.funmensaje('GENERIC'));
         else
            raise;
         end if;
      when others then
         pro_log('Linea ' || $$plsql_line || ': ' || sqlerrm);
         pkgerror.prounseterror;
         if (pkgerror.funnivelerror = 0) then
            raise_application_error(-20008, pkgerror.funmensaje('OTHERS', sqlcode, sqlerrm));
         else
            raise;
         end if;
   end pro_set_contexto;

   -----------------------------------------------------------------------------
   -- pro_aprobar_linea
   -----------------------------------------------------------------------------
   procedure pro_aprobar_linea (p_morstdem in moviresu.morstdem%type,
                                p_morstido in moviresu.morstido%type,
                                p_morsrenu in moviresu.morsrenu%type,
                                p_morsarti in moviresu.morsarti%type,
                                p_morsbode in moviresu.morsbode%type,
                                p_morssecu in moviresu.morssecu%type,
                                p_cantidad in moviresu.morscaap%type,
                                p_usuario  in varchar2)
   is
      rg_cabecera requsumi%rowtype;
      rg_linea    moviresu%rowtype;
      in_lineas   number;
      sb_inicial  varchar2(2);
      sb_aprobado varchar2(2);
      sb_tidosali varchar2(4);
      -- Se calculan fuera del UPDATE por la misma razon que arriba.
      in_hora     moviresu.morshoap%type := fun_hora;
      in_minuto   moviresu.morsmiap%type := fun_minuto;
   begin
      pkgerror.proseterror('PKG_FI_REQUISICION.pro_aprobar_linea');

      pro_valida_usuario(p_usuario);

      -- Un tipo sin equivalencia no es gestionable por este modulo, y no lo es
      -- desde el primer paso: dejar aprobar algo que despues no se podra
      -- registrar solo mueve el error mas lejos del usuario. Aqui solo interesa
      -- que la equivalencia exista; el valor se usa al registrar.
      sb_tidosali := fun_tipo_salida(p_morstido);

      sb_inicial  := fun_parametro(c_estadoinic);
      sb_aprobado := fun_parametro(c_estadoaprob);

      pro_bloquea_requi(p_morstdem, p_morstido, p_morsrenu, rg_cabecera, in_lineas);

      rg_linea := fun_bloquea_linea(p_morstdem, p_morstido, p_morsrenu,
                                    p_morsarti, p_morsbode, p_morssecu);

      if (rg_linea.morsesta <> sb_inicial) then
         pro_error('La linea [' || p_morsarti || '/' || p_morsbode || '/' || p_morssecu ||
                   '] ya fue procesada, esta en estado [' || rg_linea.morsesta || ']');
         raise pkgerror.generic;
      end if;

      if (p_cantidad is null or p_cantidad <= 0) then
         pro_error('La cantidad a aprobar de la linea [' || p_morsarti ||
                   '] debe ser mayor que cero');
         raise pkgerror.generic;
      end if;

      if (p_cantidad > rg_linea.morscaso) then
         pro_error('No se puede aprobar mas de lo solicitado en la linea [' || p_morsarti ||
                   ']: solicitado ' || rg_linea.morscaso || ', solicita aprobar ' || p_cantidad);
         raise pkgerror.generic;
      end if;

      -- El AND del estado es redundante bajo el bloqueo y se deja igual: es la
      -- red que detectaria una regresion del bloqueo en un refactor futuro.
      update moviresu
         set morscaap = p_cantidad,
             morscape = rg_linea.morscaso - p_cantidad,
             morsesta = sb_aprobado,
             morsusap = p_usuario,
             morsfeap = trunc(sysdate),
             morshoap = in_hora,
             morsmiap = in_minuto
       where morstdem = p_morstdem
         and morstido = p_morstido
         and morsrenu = p_morsrenu
         and morsarti = p_morsarti
         and morsbode = p_morsbode
         and morssecu = p_morssecu
         and morsesta = sb_inicial;

      if (sql%rowcount <> 1) then
         pro_error('No se pudo aprobar la linea [' || p_morsarti || '/' || p_morsbode ||
                   '/' || p_morssecu || ']: se afectaron ' || sql%rowcount || ' filas');
         raise pkgerror.generic;
      end if;

      pkgerror.prounseterror;
   exception
      when pkgerror.generic then
         pkgerror.prounseterror;
         if (pkgerror.funnivelerror = 0) then
            raise_application_error(-20008, pkgerror.funmensaje('GENERIC'));
         else
            raise;
         end if;
      when others then
         pro_log('Linea ' || $$plsql_line || ': ' || sqlerrm);
         pkgerror.prounseterror;
         if (pkgerror.funnivelerror = 0) then
            raise_application_error(-20008, pkgerror.funmensaje('OTHERS', sqlcode, sqlerrm));
         else
            raise;
         end if;
   end pro_aprobar_linea;

   -----------------------------------------------------------------------------
   -- pro_entregar_linea
   -----------------------------------------------------------------------------
   procedure pro_entregar_linea (p_morstdem in moviresu.morstdem%type,
                                 p_morstido in moviresu.morstido%type,
                                 p_morsrenu in moviresu.morsrenu%type,
                                 p_morsarti in moviresu.morsarti%type,
                                 p_morsbode in moviresu.morsbode%type,
                                 p_morssecu in moviresu.morssecu%type,
                                 p_cantidad in moviresu.morscaen%type,
                                 p_usuario  in varchar2)
   is
      rg_cabecera requsumi%rowtype;
      rg_linea    moviresu%rowtype;
      in_lineas   number;
      in_entrega  number;
      sb_aprobado varchar2(2);
      sb_entrega  varchar2(2);
   begin
      pkgerror.proseterror('PKG_FI_REQUISICION.pro_entregar_linea');

      pro_valida_usuario(p_usuario);

      sb_aprobado := fun_parametro(c_estadoaprob);
      sb_entrega  := fun_parametro(c_estadoentr);

      pro_bloquea_requi(p_morstdem, p_morstido, p_morsrenu, rg_cabecera, in_lineas);

      rg_linea := fun_bloquea_linea(p_morstdem, p_morstido, p_morsrenu,
                                    p_morsarti, p_morsbode, p_morssecu);

      if (rg_linea.morsesta not in (sb_aprobado, sb_entrega)) then
         pro_error('La linea [' || p_morsarti || '/' || p_morsbode || '/' || p_morssecu ||
                   '] no se puede entregar en estado [' || rg_linea.morsesta || ']');
         raise pkgerror.generic;
      end if;

      if (p_cantidad is null or p_cantidad <= 0) then
         pro_error('La cantidad a entregar de la linea [' || p_morsarti ||
                   '] debe ser mayor que cero');
         raise pkgerror.generic;
      end if;

      in_entrega := nvl(rg_linea.morscaen, 0) + p_cantidad;

      if (in_entrega > rg_linea.morscaap) then
         pro_error('No se puede entregar mas de lo aprobado en la linea [' || p_morsarti ||
                   ']: aprobado ' || rg_linea.morscaap || ', ya entregado ' ||
                   nvl(rg_linea.morscaen, 0) || ', solicita entregar ' || p_cantidad);
         raise pkgerror.generic;
      end if;

      -- Las placas se completan ANTES del UPDATE porque es ese mismo UPDATE el
      -- que dispara la validacion del trigger. Si el almacenista ya mando las
      -- suyas, esto no hace nada.
      pro_asignar_placas_fifo(p_morstdem, p_morstido, p_morsrenu,
                              p_morsarti, p_morsbode, p_morssecu, in_entrega);

      -- Este UPDATE dispara el trigger del ERP, que valida la disponibilidad en
      -- ARTIBODE y, para devolutivos, que ASPLMORS tenga tantas placas como
      -- unidades. MORSCARE no se toca aqui: se sella al registrar.
      update moviresu
         set morscaen = in_entrega,
             morscape = rg_linea.morscaso - in_entrega - nvl(rg_linea.morscaan, 0),
             morsesta = sb_entrega
       where morstdem = p_morstdem
         and morstido = p_morstido
         and morsrenu = p_morsrenu
         and morsarti = p_morsarti
         and morsbode = p_morsbode
         and morssecu = p_morssecu
         and morsesta in (sb_aprobado, sb_entrega);

      if (sql%rowcount <> 1) then
         pro_error('No se pudo entregar la linea [' || p_morsarti || '/' || p_morsbode ||
                   '/' || p_morssecu || ']: se afectaron ' || sql%rowcount || ' filas');
         raise pkgerror.generic;
      end if;

      pro_abrir_firmas(p_morstdem, p_morstido, p_morsrenu, p_usuario);

      pkgerror.prounseterror;
   exception
      when pkgerror.generic then
         pkgerror.prounseterror;
         if (pkgerror.funnivelerror = 0) then
            raise_application_error(-20008, pkgerror.funmensaje('GENERIC'));
         else
            raise;
         end if;
      when others then
         pro_log('Linea ' || $$plsql_line || ': ' || sqlerrm);
         pkgerror.prounseterror;
         if (pkgerror.funnivelerror = 0) then
            raise_application_error(-20008, pkgerror.funmensaje('OTHERS', sqlcode, sqlerrm));
         else
            raise;
         end if;
   end pro_entregar_linea;

   -----------------------------------------------------------------------------
   -- pro_asignar_placa
   -----------------------------------------------------------------------------
   procedure pro_asignar_placa (p_morstdem in moviresu.morstdem%type,
                                p_morstido in moviresu.morstido%type,
                                p_morsrenu in moviresu.morsrenu%type,
                                p_morsarti in moviresu.morsarti%type,
                                p_morssecu in moviresu.morssecu%type,
                                p_placa    in asplmors.asplplac%type)
   is
   begin
      pkgerror.proseterror('PKG_FI_REQUISICION.pro_asignar_placa');

      if (p_placa is null) then
         pro_error('No se recibio la placa a asignar al articulo [' || p_morsarti || ']');
         raise pkgerror.generic;
      end if;

      begin
         insert into asplmors (aspltdem, aspltido, asplnume, asplsecu, asplarti, asplplac)
         values (p_morstdem, p_morstido, p_morsrenu, p_morssecu, p_morsarti, p_placa);
      exception
         when dup_val_on_index then
            null;
      end;

      pkgerror.prounseterror;
   exception
      when pkgerror.generic then
         pkgerror.prounseterror;
         if (pkgerror.funnivelerror = 0) then
            raise_application_error(-20008, pkgerror.funmensaje('GENERIC'));
         else
            raise;
         end if;
      when others then
         pro_log('Linea ' || $$plsql_line || ': ' || sqlerrm);
         pkgerror.prounseterror;
         if (pkgerror.funnivelerror = 0) then
            raise_application_error(-20008, pkgerror.funmensaje('OTHERS', sqlcode, sqlerrm));
         else
            raise;
         end if;
   end pro_asignar_placa;

   -----------------------------------------------------------------------------
   -- pro_soltar_placa
   -----------------------------------------------------------------------------
   procedure pro_soltar_placa (p_morstdem in moviresu.morstdem%type,
                               p_morstido in moviresu.morstido%type,
                               p_morsrenu in moviresu.morsrenu%type,
                               p_morsarti in moviresu.morsarti%type,
                               p_morssecu in moviresu.morssecu%type,
                               p_placa    in asplmors.asplplac%type)
   is
   begin
      pkgerror.proseterror('PKG_FI_REQUISICION.pro_soltar_placa');

      delete from asplmors
       where aspltdem = p_morstdem
         and aspltido = p_morstido
         and asplnume = p_morsrenu
         and asplsecu = p_morssecu
         and asplarti = p_morsarti
         and asplplac = p_placa;

      pkgerror.prounseterror;
   exception
      when pkgerror.generic then
         pkgerror.prounseterror;
         if (pkgerror.funnivelerror = 0) then
            raise_application_error(-20008, pkgerror.funmensaje('GENERIC'));
         else
            raise;
         end if;
      when others then
         pro_log('Linea ' || $$plsql_line || ': ' || sqlerrm);
         pkgerror.prounseterror;
         if (pkgerror.funnivelerror = 0) then
            raise_application_error(-20008, pkgerror.funmensaje('OTHERS', sqlcode, sqlerrm));
         else
            raise;
         end if;
   end pro_soltar_placa;

   -----------------------------------------------------------------------------
   -- pro_anular_linea
   --
   -- Solo se anula lo que aun no se ha entregado. Anular una linea con entrega
   -- parcial la sacaria de 'en', y como PRO_GENERA_SALIREQU solo lee 'en', esa
   -- entrega no llegaria nunca al inventario.
   -----------------------------------------------------------------------------
   procedure pro_anular_linea (p_morstdem in moviresu.morstdem%type,
                               p_morstido in moviresu.morstido%type,
                               p_morsrenu in moviresu.morsrenu%type,
                               p_morsarti in moviresu.morsarti%type,
                               p_morsbode in moviresu.morsbode%type,
                               p_morssecu in moviresu.morssecu%type,
                               p_usuario  in varchar2)
   is
      rg_cabecera requsumi%rowtype;
      rg_linea    moviresu%rowtype;
      in_lineas   number;
      sb_aprobado varchar2(2);
      sb_anulaen  varchar2(2);
   begin
      pkgerror.proseterror('PKG_FI_REQUISICION.pro_anular_linea');

      pro_valida_usuario(p_usuario);

      sb_aprobado := fun_parametro(c_estadoaprob);
      sb_anulaen  := fun_parametro(c_estadoanuen);

      pro_bloquea_requi(p_morstdem, p_morstido, p_morsrenu, rg_cabecera, in_lineas);

      rg_linea := fun_bloquea_linea(p_morstdem, p_morstido, p_morsrenu,
                                    p_morsarti, p_morsbode, p_morssecu);

      if (rg_linea.morsesta <> sb_aprobado) then
         pro_error('Solo se puede anular al entregar una linea aprobada. La linea [' ||
                   p_morsarti || '/' || p_morsbode || '/' || p_morssecu ||
                   '] esta en estado [' || rg_linea.morsesta || ']');
         raise pkgerror.generic;
      end if;

      if (nvl(rg_linea.morscaen, 0) <> 0) then
         pro_error('La linea [' || p_morsarti || '] ya tiene ' || rg_linea.morscaen ||
                   ' unidad(es) entregada(s) y no se puede anular');
         raise pkgerror.generic;
      end if;

      update moviresu
         set morscaan = rg_linea.morscaap,
             morscape = rg_linea.morscaso - rg_linea.morscaap,
             morsesta = sb_anulaen
       where morstdem = p_morstdem
         and morstido = p_morstido
         and morsrenu = p_morsrenu
         and morsarti = p_morsarti
         and morsbode = p_morsbode
         and morssecu = p_morssecu
         and morsesta = sb_aprobado;

      if (sql%rowcount <> 1) then
         pro_error('No se pudo anular la linea [' || p_morsarti || '/' || p_morsbode ||
                   '/' || p_morssecu || ']: se afectaron ' || sql%rowcount || ' filas');
         raise pkgerror.generic;
      end if;

      pkgerror.prounseterror;
   exception
      when pkgerror.generic then
         pkgerror.prounseterror;
         if (pkgerror.funnivelerror = 0) then
            raise_application_error(-20008, pkgerror.funmensaje('GENERIC'));
         else
            raise;
         end if;
      when others then
         pro_log('Linea ' || $$plsql_line || ': ' || sqlerrm);
         pkgerror.prounseterror;
         if (pkgerror.funnivelerror = 0) then
            raise_application_error(-20008, pkgerror.funmensaje('OTHERS', sqlcode, sqlerrm));
         else
            raise;
         end if;
   end pro_anular_linea;

   -----------------------------------------------------------------------------
   -- pro_registrar_firma
   -----------------------------------------------------------------------------
   procedure pro_registrar_firma (p_morstdem   in moviresu.morstdem%type,
                                  p_morstido   in moviresu.morstido%type,
                                  p_morsrenu   in moviresu.morsrenu%type,
                                  p_tipo_firma in varchar2,
                                  p_persona    in fi_requfirm.reficodi%type,
                                  p_firma      in blob,
                                  p_usuario    in varchar2)
   is
      rg_cabecera requsumi%rowtype;
      in_lineas   number;
      in_posicion number;
      dt_firmada  fi_requfirm.refifefi%type;
      sb_estado   varchar2(2);
      sb_entrega  varchar2(2);
   begin
      pkgerror.proseterror('PKG_FI_REQUISICION.pro_registrar_firma');

      pro_valida_usuario(p_usuario);

      in_posicion := fun_posicion_firma(p_tipo_firma);

      if (p_firma is null or dbms_lob.getlength(p_firma) = 0) then
         pro_error('La firma [' || p_tipo_firma || '] llego vacia');
         raise pkgerror.generic;
      end if;

      if (p_persona is null) then
         pro_error('No se recibio la persona que firma');
         raise pkgerror.generic;
      end if;

      sb_entrega := fun_parametro(c_estadoentr);

      pro_bloquea_requi(p_morstdem, p_morstido, p_morsrenu, rg_cabecera, in_lineas);

      sb_estado := fun_estado(p_morstdem, p_morstido, p_morsrenu);

      if (sb_estado <> sb_entrega) then
         pro_error('La requisicion [' || p_morstdem || '-' || p_morstido || '-' || p_morsrenu ||
                   '] no admite firma en estado [' || sb_estado || ']: hay que entregarla primero');
         raise pkgerror.generic;
      end if;

      begin
         select refifefi
           into dt_firmada
           from fi_requfirm
          where refiempr = p_morstdem
            and refitido = p_morstido
            and refinume = p_morsrenu
            and refiposi = in_posicion
            for update;
      exception
         when no_data_found then
            pro_error('La requisicion [' || p_morstdem || '-' || p_morstido || '-' ||
                      p_morsrenu || '] aun no tiene firmas abiertas');
            raise pkgerror.generic;
      end;

      if (dt_firmada is not null) then
         pro_error('La firma [' || p_tipo_firma || '] de la requisicion [' || p_morstdem ||
                   '-' || p_morstido || '-' || p_morsrenu || '] ya fue registrada el ' ||
                   to_char(dt_firmada, 'DD/MM/YYYY HH24:MI:SS'));
         raise pkgerror.generic;
      end if;

      -- La fecha y la imagen se escriben a la vez: es lo que sostiene la
      -- coherencia entre las dos columnas, que no puede ir como CHECK porque
      -- Oracle no admite LOB en constraints.
      update fi_requfirm
         set refifefi = systimestamp,
             refifipe = p_firma,
             reficodi = p_persona,
             refiupat = systimestamp,
             refiupby = p_usuario
       where refiempr = p_morstdem
         and refitido = p_morstido
         and refinume = p_morsrenu
         and refiposi = in_posicion;

      if (sql%rowcount <> 1) then
         pro_error('No se pudo registrar la firma [' || p_tipo_firma || ']');
         raise pkgerror.generic;
      end if;

      pkgerror.prounseterror;
   exception
      when pkgerror.generic then
         pkgerror.prounseterror;
         if (pkgerror.funnivelerror = 0) then
            raise_application_error(-20008, pkgerror.funmensaje('GENERIC'));
         else
            raise;
         end if;
      when others then
         pro_log('Linea ' || $$plsql_line || ': ' || sqlerrm);
         pkgerror.prounseterror;
         if (pkgerror.funnivelerror = 0) then
            raise_application_error(-20008, pkgerror.funmensaje('OTHERS', sqlcode, sqlerrm));
         else
            raise;
         end if;
   end pro_registrar_firma;

   -----------------------------------------------------------------------------
   -- pro_recibir_linea
   -----------------------------------------------------------------------------
   procedure pro_recibir_linea (p_morstdem in moviresu.morstdem%type,
                                p_morstido in moviresu.morstido%type,
                                p_morsrenu in moviresu.morsrenu%type,
                                p_morsarti in moviresu.morsarti%type,
                                p_morsbode in moviresu.morsbode%type,
                                p_morssecu in moviresu.morssecu%type,
                                p_cantidad in moviresu.morscare%type,
                                p_usuario  in varchar2)
   is
      rg_cabecera requsumi%rowtype;
      rg_linea    moviresu%rowtype;
      in_lineas   number;
      sb_entrega  varchar2(2);
   begin
      pkgerror.proseterror('PKG_FI_REQUISICION.pro_recibir_linea');

      pro_valida_usuario(p_usuario);

      sb_entrega := fun_parametro(c_estadoentr);

      pro_bloquea_requi(p_morstdem, p_morstido, p_morsrenu, rg_cabecera, in_lineas);

      rg_linea := fun_bloquea_linea(p_morstdem, p_morstido, p_morsrenu,
                                    p_morsarti, p_morsbode, p_morssecu);

      if (rg_linea.morsesta <> sb_entrega) then
         pro_error('La linea [' || p_morsarti || '/' || p_morsbode || '/' || p_morssecu ||
                   '] no esta entregada, esta en estado [' || rg_linea.morsesta || ']');
         raise pkgerror.generic;
      end if;

      -- Recibir cero no es recibir: dejaria la linea en 'rg' sin movimiento de
      -- inventario. Lo que no se recibe se anula, que es otro camino.
      if (p_cantidad is null or p_cantidad <= 0) then
         pro_error('La cantidad recibida de la linea [' || p_morsarti ||
                   '] debe ser mayor que cero. Si no se recibe nada, hay que anular la linea');
         raise pkgerror.generic;
      end if;

      if (p_cantidad > rg_linea.morscaen) then
         pro_error('No se puede recibir mas de lo entregado en la linea [' || p_morsarti ||
                   ']: entregado ' || rg_linea.morscaen || ', solicita recibir ' || p_cantidad);
         raise pkgerror.generic;
      end if;

      -- Este UPDATE dispara la validacion de placas del trigger: para un
      -- devolutivo, ASPLMORS debe tener exactamente p_cantidad placas. Si se
      -- recibe parcial hay que haber soltado antes las que no se reciben.
      update moviresu
         set morscare = p_cantidad
       where morstdem = p_morstdem
         and morstido = p_morstido
         and morsrenu = p_morsrenu
         and morsarti = p_morsarti
         and morsbode = p_morsbode
         and morssecu = p_morssecu
         and morsesta = sb_entrega;

      if (sql%rowcount <> 1) then
         pro_error('No se pudo sellar la cantidad recibida de la linea [' || p_morsarti ||
                   '/' || p_morsbode || '/' || p_morssecu || ']');
         raise pkgerror.generic;
      end if;

      pkgerror.prounseterror;
   exception
      when pkgerror.generic then
         pkgerror.prounseterror;
         if (pkgerror.funnivelerror = 0) then
            raise_application_error(-20008, pkgerror.funmensaje('GENERIC'));
         else
            raise;
         end if;
      when others then
         pro_log('Linea ' || $$plsql_line || ': ' || sqlerrm);
         pkgerror.prounseterror;
         if (pkgerror.funnivelerror = 0) then
            raise_application_error(-20008, pkgerror.funmensaje('OTHERS', sqlcode, sqlerrm));
         else
            raise;
         end if;
   end pro_recibir_linea;

   -----------------------------------------------------------------------------
   -- pro_generar_salida
   --
   -- El unico procedimiento que toca el ERP, y el unico cuyo efecto no se puede
   -- deshacer desde la aplicacion.
   --
   -- SOBRE EL RE-RAISE DEL ORA-20008
   --
   -- PRO_GENERA_SALIREQU llama a pkgerror.proseterror al entrar, asi que cuando
   -- lanza su raise_application_error(-20008) y la excepcion sube hasta el WHEN
   -- OTHERS de aqui, funnivelerror ya no vale 0 y se ejecuta la rama 'raise'.
   -- El mensaje de negocio del ERP llega intacto al driver en vez de quedar
   -- sepultado bajo un segundo -20008 generico. Si alguien sustituye este bloque
   -- por un raise_application_error plano, ese mensaje se pierde.
   -----------------------------------------------------------------------------
   procedure pro_generar_salida (p_morstdem in  moviresu.morstdem%type,
                                 p_morstido in  moviresu.morstido%type,
                                 p_morsrenu in  moviresu.morsrenu%type,
                                 p_usuario  in  varchar2,
                                 p_tidosali out varchar2,
                                 p_mensaje  out varchar2)
   is
      rg_cabecera  requsumi%rowtype;
      in_lineas    number;
      in_firmas    number;
      in_sinfirmar number;
      in_pendi     number;
      in_entre     number;
      in_queda     number;
      in_ajenas1   number;
      in_ajenas2   number;
      sb_tidosali  varchar2(4);
      sb_estado    varchar2(2);
      sb_entrega   varchar2(2);
      sb_registra  varchar2(2);
      sb_anulaen   varchar2(2);
      -- El formal sbText es IN OUT sin tamano, asi que el tamano del ACTUAL es
      -- el que gobierna. Con un varchar2 corto el ERP podria reventar con
      -- ORA-06502 al escribir en el.
      sb_text      varchar2(4000);
   begin
      pkgerror.proseterror('PKG_FI_REQUISICION.pro_generar_salida');

      pro_valida_usuario(p_usuario);

      sb_entrega  := fun_parametro(c_estadoentr);
      sb_registra := fun_parametro(c_estadoregi);
      sb_anulaen  := fun_parametro(c_estadoanuen);

      pro_bloquea_requi(p_morstdem, p_morstido, p_morsrenu, rg_cabecera, in_lineas);

      -- Reentrada. Es lo que impide que un reintento del cliente genere un
      -- segundo documento de salida, que seria irreversible.
      sb_estado := fun_estado(p_morstdem, p_morstido, p_morsrenu);

      if (sb_estado = sb_registra) then
         p_tidosali := null;
         p_mensaje  := 'La requisicion [' || p_morstdem || '-' || p_morstido || '-' ||
                       p_morsrenu || '] ya fue registrada';
         pkgerror.prounseterror;
         return;
      end if;

      -- Las dos firmas, sin orden entre ellas.
      select count(1)
        into in_firmas
        from fi_requfirm
       where refiempr = p_morstdem
         and refitido = p_morstido
         and refinume = p_morsrenu;

      select count(1)
        into in_sinfirmar
        from fi_requfirm
       where refiempr = p_morstdem
         and refitido = p_morstido
         and refinume = p_morsrenu
         and refifefi is null;

      if (in_firmas <> 2 or in_sinfirmar > 0) then
         pro_error('La requisicion [' || p_morstdem || '-' || p_morstido || '-' || p_morsrenu ||
                   '] no tiene las dos firmas: faltan ' || (2 - in_firmas + in_sinfirmar));
         raise pkgerror.generic;
      end if;

      -- Todas las lineas resueltas, y al menos una que entregar.
      select count(1)
        into in_pendi
        from moviresu
       where morstdem = p_morstdem
         and morstido = p_morstido
         and morsrenu = p_morsrenu
         and morsesta not in (sb_entrega, sb_anulaen);

      if (in_pendi > 0) then
         pro_error('La requisicion [' || p_morstdem || '-' || p_morstido || '-' || p_morsrenu ||
                   '] tiene ' || in_pendi || ' linea(s) sin entregar ni anular');
         raise pkgerror.generic;
      end if;

      select count(1)
        into in_entre
        from moviresu
       where morstdem = p_morstdem
         and morstido = p_morstido
         and morsrenu = p_morsrenu
         and morsesta = sb_entrega;

      if (in_entre = 0) then
         pro_error('La requisicion [' || p_morstdem || '-' || p_morstido || '-' || p_morsrenu ||
                   '] no tiene ninguna linea entregada que registrar');
         raise pkgerror.generic;
      end if;

      -- Sellar la cantidad recibida de las lineas que no se recibieron
      -- parciales. SIN ESTO la linea pasaria a 'rg' sin generar movimiento de
      -- inventario: PRO_GENERA_SALIREQU salta las que tienen MORSCARE en cero.
      update moviresu
         set morscare = morscaen
       where morstdem = p_morstdem
         and morstido = p_morstido
         and morsrenu = p_morsrenu
         and morsesta = sb_entrega
         and nvl(morscare, 0) = 0;

      sb_tidosali := fun_tipo_salida(p_morstido);

      -- Testigo del LIKE. El cursor de PRO_GENERA_SALIREQU filtra
      -- "morsrenu LIKE sbMoReNume", asi que un comodin barreria lineas de otras
      -- requisiciones. Se cuenta antes y despues para detectarlo.
      select count(1)
        into in_ajenas1
        from moviresu
       where morsesta = sb_entrega
         and not (morstdem = p_morstdem and morstido = p_morstido and morsrenu = p_morsrenu);

      -- El numero se toma del ROWTYPE leido bajo bloqueo, nunca del parametro
      -- de entrada: asi lo que viaja al ERP es siempre un NUMBER de la propia
      -- base y no puede contener un comodin.
      pro_genera_salirequ(sbmoreempr => p_morstdem,
                          sbtidosare => sb_tidosali,
                          sbmorenume => to_char(rg_cabecera.resunume),
                          sbtiponume => c_numeauto,
                          sbtext     => sb_text,
                          dtfecha    => sysdate,
                          nusigunume => 0,
                          sbtidoreqi => p_morstido);

      -- Poscondicion: no puede quedar ninguna linea sin registrar.
      select count(1)
        into in_queda
        from moviresu
       where morstdem = p_morstdem
         and morstido = p_morstido
         and morsrenu = p_morsrenu
         and morsesta = sb_entrega;

      if (in_queda > 0) then
         pro_error('El ERP no registro ' || in_queda || ' linea(s) de la requisicion [' ||
                   p_morstdem || '-' || p_morstido || '-' || p_morsrenu || ']: ' || sb_text);
         raise pkgerror.generic;
      end if;

      select count(1)
        into in_ajenas2
        from moviresu
       where morsesta = sb_entrega
         and not (morstdem = p_morstdem and morstido = p_morstido and morsrenu = p_morsrenu);

      if (in_ajenas2 < in_ajenas1) then
         pro_error('PRO_GENERA_SALIREQU afecto ' || (in_ajenas1 - in_ajenas2) ||
                   ' linea(s) de otras requisiciones. Se aborta la operacion');
         raise pkgerror.generic;
      end if;

      p_tidosali := sb_tidosali;
      p_mensaje  := sb_text;

      pro_log('Requisicion [' || p_morstdem || '-' || p_morstido || '-' || p_morsrenu ||
              '] registrada por ' || p_usuario || ' con tipo de salida ' || sb_tidosali);

      pkgerror.prounseterror;
   exception
      when pkgerror.generic then
         pkgerror.prounseterror;
         if (pkgerror.funnivelerror = 0) then
            raise_application_error(-20008, pkgerror.funmensaje('GENERIC'));
         else
            raise;
         end if;
      when others then
         pro_log('Linea ' || $$plsql_line || ': ' || sqlerrm);
         pkgerror.prounseterror;
         if (pkgerror.funnivelerror = 0) then
            raise_application_error(-20008, pkgerror.funmensaje('OTHERS', sqlcode, sqlerrm));
         else
            raise;
         end if;
   end pro_generar_salida;

end pkg_fi_requisicion;
/
