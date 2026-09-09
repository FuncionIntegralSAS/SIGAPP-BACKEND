create or replace package body pkg_fi_movitras is

   -----------------------------------------------------------------------------
   -- COMPILAR EN EL ESQUEMA DE RECURSOS FISICOS SRF (SRFPNACUA), dueno de
   -- FI_MOVITRAS y FI_MOTRFIRM, y del que dependen sin calificar las tablas del
   -- ERP que se usan aqui (REQUSUMI, ACTIFIJO, PARAOPER, TIMOOPER, TIPODOCU,
   -- DOCUINVE, MOVIINVE, ARTICULO, PERSCECO, TIDOEMNU).
   --
   -- INVARIANTE DEL MODULO
   --
   -- FI_MOVITRAS tiene una fila por articulo y MOTRNUTR agrupa las del mismo
   -- tramite. De MOTRDREM a MOTRFERE el contenido se repite en las N lineas, asi
   -- que TODO UPDATE de este paquete filtra por MOTRNUTR y ninguno por MOTRIDMT.
   -- Romper esa regla parte el tramite en dos estados sin que nada lo impida.
   --
   -- Por lo mismo, ningun procedimiento hace SELECT INTO por el identificador del
   -- tramite: devolveria N filas. Se usa pro_bloquea_tramite.
   -----------------------------------------------------------------------------

   -----------------------------------------------------------------------------
   -- Claves del maestro de parametros del sistema (PARAOPER, se leen con
   -- FunBuscPara sobre PARANOPR y devuelven PARACOVA)
   -----------------------------------------------------------------------------
   c_tipomovi      varchar2(10) := 'TIMOTRIN'; -- Tipo de movimiento del traspaso
   c_tipobodega    varchar2(10) := 'BODEPERS'; -- Tipo de bodega de una division
   c_bodelogica    varchar2(10) := 'BOALLOGI'; -- Bodega logica
   c_conceptomovi  varchar2(10) := 'CONCDEVO'; -- Concepto por defecto para devolutivos
   c_estadoactivo  varchar2(10) := 'ESTAACTI'; -- Estado activo
   c_estadoregi    varchar2(10) := 'ESTAREGI'; -- Estado registrado (movimiento recien creado)
   -- Estado con el que el ERP marca un documento anulado. Misma clave que usa
   -- TRGBUPT_REQUSUMI, para no inventar una segunda fuente de verdad.
   c_estadoanula   varchar2(10) := 'ESTAANUL';
   -- Estado operativo al que pasa el DOCUMENTO REQUSUMI cuando el traspaso se
   -- aprueba. Ojo: es del dominio del ERP, no del tramite. El estado del tramite
   -- es la constante c_aprobado y no se parametriza, porque FI_MOVITRAS lo
   -- restringe con un CHECK.
   --
   -- REVISAR AL DESPLEGAR: 06_PARAOPER.sql da de alta ESAPTRAS ("estado al
   -- aprobar traspaso") y en cambio aqui se lee ESTAAPRO, que ese script no
   -- crea. Se conserva la clave que el codigo venia usando para no cambiar el
   -- comportamiento en un refactor, pero una de las dos sobra:
   --
   --   SELECT paranopr, paracova FROM paraoper
   --    WHERE paranopr IN ('ESTAAPRO','ESAPTRAS');
   c_estadoaprodocu varchar2(10) := 'ESTAAPRO';

   -- Operacion que el tipo de movimiento debe tener habilitada en TIMOOPER para
   -- poder usarse en un traspaso. Es tambien la que queda marcada en el
   -- movimiento de inventario (MOINOPER / MOINTIOP).
   c_operacion     varchar2(20) := 'TRASPASO';

   -- Valores fijos del movimiento de inventario: cada linea mueve una unidad del
   -- elemento, sin factor de conversion ni valores agregados.
   c_cantidad      number := 1;
   c_facconv       number := 1;
   c_cero          number := 0;
   c_no            varchar2(1) := 'N';
   c_si            varchar2(1) := 'S';
   c_docuvacio     varchar2(1) := '.';

   -- Placa de los elementos que no tienen. Igual que ACTIFIJO: un NULL no
   -- deduplica en el indice unico de la linea.
   c_placavacia    varchar2(1) := '.';

   -----------------------------------------------------------------------------
   -- Estados de FI_MOVITRAS. Literales, no parametros: los restringe el CHECK
   -- FI_MOVITRAS_CK_ESTA.
   --
   -- No existen 'af' ni 'ad'. Al no haber orden entre las firmas, el avance de
   -- firma son dos booleanos independientes y una sola columna no puede
   -- representarlos: se consulta en FI_MOTRFIRM.
   -----------------------------------------------------------------------------
   c_pendiente     varchar2(2) := 'pe';
   c_aprobado      varchar2(2) := 'ap';
   c_rechazado     varchar2(2) := 'na';
   c_recibido      varchar2(2) := 're';

   -- Tipos de firma que recibe el backend y su posicion en FI_MOTRFIRM
   c_firmafuente   varchar2(2) := 'FU';
   c_firmadestino  varchar2(2) := 'DE';
   c_posifuente    number := 1;   -- entrega
   c_posidestino   number := 2;   -- recibe

   -- Objeto con el que se identifican los errores del modulo en el log del ERP.
   c_tabla         varchar2(30) := 'FI_MOVITRAS';


   -----------------------------------------------------------------------------
   -- pro_log
   -----------------------------------------------------------------------------
   procedure pro_log (p_mensaje in varchar2)
   is

   begin
      pkgerrointe.procrear(c_tabla,user,systimestamp|| ' '|| p_mensaje);
   exception
      when others then
         null;
   end pro_log;

   -----------------------------------------------------------------------------
   -- pro_error
   --
   -- Error de negocio. Hace las dos cosas que hay que hacer con el mensaje, que
   -- no son intercambiables:
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
      if ( sb_valor is null ) then
         pro_error('El parametro ['|| p_clave || '] no esta configurado');
         raise pkgerror.generic;
      end if;

      return sb_valor;
   end fun_parametro;


   -----------------------------------------------------------------------------
   -- fun_tipodocu
   -- Deriva el tipo de documento a partir del tipo de movimiento.
   --   1) TIMOOPER: el tipo de movimiento debe tener habilitada la operacion TRASPASO.
   --   2) TIPODOCU: TIDOTIDO es el tipo de documento asociado (TIDOTIMO).
   -----------------------------------------------------------------------------
   function fun_tipodocu (p_timo in varchar2)
   return varchar2 is
      in_existe   number;
      sb_tipodocu tipodocu.tidotido%type;
   begin
      select count(1)
        into in_existe
        from timooper
       where tmoptimo = p_timo
         and tmopoper = c_operacion;

      if ( in_existe = 0 ) then
         pro_error('El tipo de movimiento ['|| p_timo || '] no existe o no tiene configurada la operacion ['
                   || c_operacion || ']');
         raise pkgerror.generic;
      end if;

      begin
         select tidotido
           into sb_tipodocu
           from tipodocu
          where tidotimo = p_timo;
      exception
         when no_data_found then
            pro_error('No hay tipo de documento asociado al tipo de movimiento ['|| p_timo || ']');
            raise pkgerror.generic;
         when too_many_rows then
            pro_error('El tipo de movimiento ['|| p_timo
                      || '] tiene mas de un tipo ' || 'de documento asociado en TIPODOCU');
            raise pkgerror.generic;
      end;

      return sb_tipodocu;
   end fun_tipodocu;

   -----------------------------------------------------------------------------
   -- fun_bodega_persona
   -- Bodega asociada a la division de una persona.
   -----------------------------------------------------------------------------
   function fun_bodega_persona ( p_empresa in varchar2,
                                 p_persona in varchar2 )
   return varchar2 is
      sb_bodega     varchar2(4);
      sb_tipobodega varchar2(4) := fun_parametro(c_tipobodega);
   begin
      select max(b.bodecodi)
        into sb_bodega
        from personal p
        join bodega b
      on b.bodedivi = p.persdivi
         and b.bodetibo = sb_tipobodega
         and b.bodeempr = p_empresa
       where p.perscodi = p_persona;

      if ( sb_bodega is null ) then
         pro_error('No se encontro bodega para la persona ['|| p_persona || ']');
         raise pkgerror.generic;
      end if;

      return sb_bodega;
   end fun_bodega_persona;

   -----------------------------------------------------------------------------
   -- fun_acfiidin
   --
   -- Identificador interno del activo a partir del par (elemento, placa), que es
   -- como lo identifica el traspaso. Es la llave con la que despues se lee la
   -- fila completa con pkgtbactifijo.prosel.
   --
   -- La placa se compara con NVL contra '.' porque en ACTIFIJO los elementos sin
   -- placa la guardan como punto y no como NULL: comparar directo dejaria fuera
   -- justamente esos.
   -----------------------------------------------------------------------------
   function fun_acfiidin ( p_arti  in fi_movitras.motrarti%type,
                           p_placa in fi_movitras.motrplac%type )
   return actifijo.acfiidin%type is
      sb_acfiidin actifijo.acfiidin%type;
   begin
      select acfiidin
        into sb_acfiidin
        from actifijo
       where acfiarti = p_arti
         and nvl(acfiplac,c_placavacia) = nvl(p_placa,c_placavacia);

      return sb_acfiidin;
   exception
      when no_data_found then
         pro_error('El elemento ['|| p_arti|| '] con placa ['|| p_placa || '] no existe en ACTIFIJO');
         raise pkgerror.generic;
      when too_many_rows then
         pro_error('El elemento ['|| p_arti|| '] con placa ['|| p_placa || '] esta duplicado en ACTIFIJO');
         raise pkgerror.generic;
   end fun_acfiidin;


   -----------------------------------------------------------------------------
   -- pro_valida_persona
   -----------------------------------------------------------------------------
   procedure pro_valida_persona ( p_persona in varchar2,
                                  p_rol     in varchar2 )
   is
      in_existe number;
   begin
      select count(1)
        into in_existe
        from personal
       where perscodi = p_persona;

      if ( in_existe = 0 ) then
         pro_error('La persona '|| p_rol|| ' ['|| p_persona || '] no existe');
         raise pkgerror.generic;
      end if;
   end pro_valida_persona;


   -----------------------------------------------------------------------------
   -- pro_bloquea_tramite
   --
   -- Bloquea TODAS las lineas del tramite y devuelve los datos comunes.
   --
   -- Sustituye al SELECT INTO ... FOR UPDATE de la version de un solo articulo:
   -- filtrando por MOTRNUTR eso seria TOO_MANY_ROWS. El cursor con FOR UPDATE
   -- toma el bloqueo sobre las N filas, que es lo que impide una aprobacion
   -- concurrente a medio camino.
   --
   -- La cabecera se lee despues con ROWNUM = 1, sin ORDER BY, porque cualquier
   -- linea sirve: de MOTRDREM a MOTRFERE todas guardan lo mismo. Si alguna vez
   -- no fuera asi, el invariante del modulo ya estaria roto.
   -----------------------------------------------------------------------------
   procedure pro_bloquea_tramite ( p_motrnutr  in  fi_movitras.motrnutr%type,
                                   rg_traspaso out fi_movitras%rowtype,
                                   p_lineas    out number )
   is
   begin
      p_lineas := 0;

      for rg_linea in ( select motridmt
                          from fi_movitras
                         where motrnutr = p_motrnutr
                           for update ) loop
         p_lineas := p_lineas + 1;
      end loop;

      if ( p_lineas = 0 ) then
         pro_error('No existe el traspaso ['|| p_motrnutr || ']');
         raise pkgerror.generic;
      end if;

      select *
        into rg_traspaso
        from fi_movitras
       where motrnutr = p_motrnutr
         and rownum = 1;
   end pro_bloquea_tramite;


   -----------------------------------------------------------------------------
   -- pro_valida_activo_tramite
   --
   -- Comprueba que un activo pueda entrar en un tramite que ya tiene lineas.
   --
   -- El centro de informacion y el tercero se validan porque son datos de
   -- CABECERA: RESUCEIN viaja en la requisicion y DOINTERC en el documento de
   -- inventario, uno por tramite. Si dos activos no coinciden no hay valor
   -- correcto que poner, y tomar el del primero mandaria el documento a un
   -- centro equivocado sin que nadie se entere.
   -----------------------------------------------------------------------------
   procedure pro_valida_activo_tramite ( p_motrnutr  in fi_movitras.motrnutr%type,
                                         p_motrarti  in fi_movitras.motrarti%type,
                                         p_motrplac  in fi_movitras.motrplac%type,
                                         rg_actifijo in actifijo%rowtype )
   is
      in_repetido    number;
      sb_ceintramite requsumi.resucein%type;
      rg_primero     actifijo%rowtype;
      sb_artiprimero fi_movitras.motrarti%type;
      sb_placprimero fi_movitras.motrplac%type;
   begin
      -- Repetido: se corta con mensaje propio en vez de dejar que reviente
      -- FI_MOVITRAS_UK_LINE con un ORA-00001 que no dice nada.
      select count(1)
        into in_repetido
        from fi_movitras
       where motrnutr = p_motrnutr
         and motrarti = p_motrarti
         and motrplac = nvl(p_motrplac,c_placavacia);

      if ( in_repetido > 0 ) then
         pro_error('El elemento ['|| p_motrarti|| '] con placa ['|| p_motrplac
                   || '] ya esta incluido en el traspaso ['|| p_motrnutr || ']');
         raise pkgerror.generic;
      end if;

      -- Centro de informacion del documento ya creado
      select max(r.resucein)
        into sb_ceintramite
        from fi_movitras t
        join requsumi r
          on r.resutdem = t.motrdrem
         and r.resutido = t.motrdrtd
         and r.resunume = t.motrdrnd
       where t.motrnutr = p_motrnutr;

      if ( rg_actifijo.acficein is null ) then
         pro_error('El elemento ['|| p_motrarti|| '] con placa ['|| p_motrplac
                   || '] no tiene centro de informacion en ACTIFIJO');
         raise pkgerror.generic;
      end if;

      if ( rg_actifijo.acficein != sb_ceintramite ) then
         pro_error('El elemento ['|| p_motrarti|| '] pertenece al centro de informacion ['
                   || rg_actifijo.acficein || '] y el traspaso ['|| p_motrnutr
                   || '] es del centro ['|| sb_ceintramite
                   || ']. Un traspaso no puede mezclar centros de informacion');
         raise pkgerror.generic;
      end if;

      -- Tercero: se compara contra el del primer articulo del tramite, que es el
      -- que quedara en DOCUINVE.DOINTERC.
      select min(motrarti) keep (dense_rank first order by motridmt),
             min(motrplac) keep (dense_rank first order by motridmt)
        into sb_artiprimero, sb_placprimero
        from fi_movitras
       where motrnutr = p_motrnutr;

      pkgtbactifijo.prosel(fun_acfiidin(sb_artiprimero,sb_placprimero),null,rg_primero);

      if ( nvl(rg_actifijo.acfiterc,c_docuvacio) != nvl(rg_primero.acfiterc,c_docuvacio) ) then
         pro_error('El elemento ['|| p_motrarti|| '] es del tercero ['
                   || rg_actifijo.acfiterc || '] y el traspaso ['|| p_motrnutr
                   || '] es del tercero ['|| rg_primero.acfiterc
                   || ']. Un traspaso no puede mezclar terceros');
         raise pkgerror.generic;
      end if;
   end pro_valida_activo_tramite;


   -----------------------------------------------------------------------------
   -- pro_insertar_linea
   --
   -- Inserta una linea de articulo copiando los datos comunes de la cabecera.
   -- Los recibe en un rowtype para que exista un unico sitio donde se decide que
   -- se replica en cada fila.
   -----------------------------------------------------------------------------
   procedure pro_insertar_linea ( rg_cabecera in fi_movitras%rowtype,
                                  p_motrarti  in fi_movitras.motrarti%type,
                                  p_motrplac  in fi_movitras.motrplac%type,
                                  p_usuario   in varchar2 )
   is
   begin
      insert into fi_movitras (
         motridmt,
         motrnutr,
         motrdrem,
         motrdrtd,
         motrdrnd,
         motrpefu,
         motrpede,
         motrarti,
         motrplac,
         motresta,
         motrobse,
         motrmore,
         motrfecr,
         motruscr,
         motrfeap,
         motrusap,
         motrcrby
      ) values ( fi_movitras_seq.nextval,
                 rg_cabecera.motrnutr,
                 rg_cabecera.motrdrem,
                 rg_cabecera.motrdrtd,
                 rg_cabecera.motrdrnd,
                 rg_cabecera.motrpefu,
                 rg_cabecera.motrpede,
                 p_motrarti,
                 nvl(p_motrplac,c_placavacia),
                 rg_cabecera.motresta,
                 rg_cabecera.motrobse,
                 rg_cabecera.motrmore,
                 nvl(rg_cabecera.motrfecr,systimestamp),
                 rg_cabecera.motruscr,
                 rg_cabecera.motrfeap,
                 rg_cabecera.motrusap,
                 p_usuario );
   end pro_insertar_linea;


   -----------------------------------------------------------------------------
   -- pro_crear_solicitud
   -----------------------------------------------------------------------------
   procedure pro_crear_solicitud (p_motrdrem  in fi_movitras.motrdrem%type,
                                  p_motrpefu  in fi_movitras.motrpefu%type,
                                  p_motrpede  in fi_movitras.motrpede%type,
                                  p_motrarti  in fi_movitras.motrarti%type,
                                  p_motrplac  in fi_movitras.motrplac%type,
                                  p_motrobse  in fi_movitras.motrobse%type,
                                  p_tipo_movi in varchar2,
                                  p_motruscr  in fi_movitras.motruscr%type,
                                  p_motrnutr  out fi_movitras.motrnutr%type,
                                  p_motrdrtd  out fi_movitras.motrdrtd%type,
                                  p_motrdrnd  out fi_movitras.motrdrnd%type
                                 ) is

      sb_tipomovi      timooper.tmoptimo%type;
      sb_tipodocu      requsumi.resutido%type;
      sb_estadodocu    requsumi.resuesta%type;
      sb_division      personal.persdivi%type;
      sb_ciudad        personal.persciud%type;
      sb_bodegafuente  requsumi.resubode%type;
      sb_bodegadestino requsumi.resubods%type;
      in_numedocu      requsumi.resunume%type;
      sb_acfiidin      actifijo.acfiidin%type;
      rg_actifijo      actifijo%rowtype;
      rg_cabecera      fi_movitras%rowtype;
      sb_bodelogica    varchar2(6);
      sb_concepto      varchar2(10);
   begin
      pkgerror.proseterror('PKG_FI_MOVITRAS.pro_crear_solicitud');
      sb_bodelogica := fun_parametro(c_bodelogica);
      sb_concepto   := fun_parametro(c_conceptomovi);

      -------------------------------------------------------------------------
      -- Validaciones de negocio
      -------------------------------------------------------------------------
      -- valido que la persona fuente y persona destino sean diferentes
      if ( p_motrpefu = p_motrpede ) then
         pro_error('La persona fuente y la persona destino no pueden ser la misma');
         raise pkgerror.generic;
      end if;
      --valido que las personas existan en PERSONAL
      pro_valida_persona(p_motrpefu,'fuente');
      pro_valida_persona(p_motrpede,'destino');
      --valido que exista el activo en ACTIFIJO y tomo su identificador interno
      sb_acfiidin := fun_acfiidin(p_motrarti,p_motrplac);

      -- El centro de informacion no es parametro: sale del propio activo. Cada
      -- elemento pertenece al suyo, una clave global mandaria todos los
      -- traspasos al mismo centro. Los articulos que se agreguen despues tienen
      -- que compartirlo, cosa que valida pro_agregar_articulo.
      pkgtbactifijo.prosel(sb_acfiidin,null,rg_actifijo);

      if ( rg_actifijo.acficein is null ) then
         pro_error('El elemento ['|| p_motrarti|| '] con placa ['|| p_motrplac
                   || '] no tiene centro de informacion en ACTIFIJO');
         raise pkgerror.generic;
      end if;

      -------------------------------------------------------------------------
      -- Configuracion y datos derivados
      -------------------------------------------------------------------------
      -- El tipo de movimiento puede venir del llamador; si no, se toma del
      -- parametro. En cualquiera de los dos casos pasa por la misma validacion.
      sb_tipomovi := nvl(p_tipo_movi,fun_parametro(c_tipomovi));
      sb_tipodocu := fun_tipodocu(sb_tipomovi);
      sb_estadodocu := c_pendiente;

      select persdivi, persciud
        into sb_division, sb_ciudad
        from personal
       where perscodi = p_motrpefu;

      sb_bodegafuente := fun_bodega_persona(p_motrdrem,p_motrpefu);
      sb_bodegadestino := fun_bodega_persona(p_motrdrem,p_motrpede);

      -------------------------------------------------------------------------
      -- 1) Documento: requisicion de suministro
      --
      -- RESUNUME no se envia: lo asigna TRGBINR_REQUSUMI a partir del numerador
      -- del tipo de documento. RETURNING recupera el valor ya asignado por el
      -- trigger. Si el tipo de documento NO esta marcado como automatico en
      -- TIPODOCU, num_automatico devuelve FALSE, el trigger no numera y el
      -- INSERT falla por NOT NULL: en ese caso hay que configurar el numerador.
      --
      -- Uno por tramite, aunque el traspaso mueva N articulos.
      -------------------------------------------------------------------------
      INSERT INTO requsumi (
         resutdem,resutido,resudiem,resudivi,resuciud,resufech,resuobse,resuesta,resupeso,resubode,resubods,resublfu,resublde,resucein,resucocm
      ) values ( p_motrdrem,
                 sb_tipodocu,
                 p_motrdrem,
                 sb_division,
                 sb_ciudad,
                 sysdate,
                 p_motrobse,
                 sb_estadodocu,
                 p_motrpefu,
                 sb_bodegafuente,
                 sb_bodegadestino,
                 sb_bodelogica,
                 sb_bodelogica,
                 rg_actifijo.acficein,
                 sb_concepto ) returning resunume into in_numedocu;

      -------------------------------------------------------------------------
      -- 2) Primera linea del traspaso, ya con el documento existente
      --
      -- El numero de tramite sale de su propia secuencia. Es el identificador
      -- con el que el backend opera de aqui en adelante; MOTRIDMT queda como
      -- clave tecnica de cada linea.
      -------------------------------------------------------------------------
      p_motrnutr := fi_traspaso_seq.nextval;

      rg_cabecera.motrnutr := p_motrnutr;
      rg_cabecera.motrdrem := p_motrdrem;
      rg_cabecera.motrdrtd := sb_tipodocu;
      rg_cabecera.motrdrnd := in_numedocu;
      rg_cabecera.motrpefu := p_motrpefu;
      rg_cabecera.motrpede := p_motrpede;
      rg_cabecera.motresta := c_pendiente;
      rg_cabecera.motrobse := p_motrobse;
      rg_cabecera.motrfecr := systimestamp;
      rg_cabecera.motruscr := p_motruscr;

      pro_insertar_linea(rg_cabecera,p_motrarti,p_motrplac,p_motruscr);

      -------------------------------------------------------------------------
      -- 3) Las dos firmas, vacias
      --
      -- Se crean ahora y no al firmar, por dos razones: deja registrado desde el
      -- primer momento quien tiene que firmar, y convierte pro_registrar_firma
      -- en un UPDATE, que es lo que permite que la PK impida una segunda firma
      -- sobre la misma posicion.
      -------------------------------------------------------------------------
      insert into fi_motrfirm ( mofinutr, mofiposi, moficodi, moficrby )
           values ( p_motrnutr, c_posifuente, p_motrpefu, p_motruscr );

      insert into fi_motrfirm ( mofinutr, mofiposi, moficodi, moficrby )
           values ( p_motrnutr, c_posidestino, p_motrpede, p_motruscr );

      p_motrdrtd := sb_tipodocu;
      p_motrdrnd := in_numedocu;
      pkgerror.prounseterror;
   exception
      when pkgerror.generic then
         pkgerror.prounseterror;
         if ( pkgerror.funnivelerror = 0 ) then
            raise_application_error(-20008,pkgerror.funmensaje('GENERIC'));
         else
            raise;
         end if;
      when others then
         pro_log('Linea '|| $$plsql_line|| ': ' || sqlerrm);
         pkgerror.prounseterror;
         if ( pkgerror.funnivelerror = 0 ) then
            raise_application_error(-20008,pkgerror.funmensaje('OTHERS',sqlcode,sqlerrm));
         else
            raise;
         end if;
   end pro_crear_solicitud;


   -----------------------------------------------------------------------------
   -- pro_agregar_articulo
   --
   -- Anade un articulo a un tramite ya abierto. Copia los datos comunes de una
   -- linea existente, de modo que las N filas del tramite siempre digan lo mismo.
   -----------------------------------------------------------------------------
   procedure pro_agregar_articulo (p_motrnutr in fi_movitras.motrnutr%type,
                                   p_motrarti in fi_movitras.motrarti%type,
                                   p_motrplac in fi_movitras.motrplac%type,
                                   p_usuario  in varchar2
                                  ) is
      rg_cabecera fi_movitras%rowtype;
      rg_actifijo actifijo%rowtype;
      in_lineas   number;
   begin
      pkgerror.proseterror('PKG_FI_MOVITRAS.pro_agregar_articulo');

      pro_bloquea_tramite(p_motrnutr,rg_cabecera,in_lineas);

      -- El aprobador autorizo un conjunto concreto de articulos: colar uno mas
      -- despues de la aprobacion saltaria esa autorizacion.
      if ( rg_cabecera.motresta != c_pendiente ) then
         pro_error('El traspaso ['|| p_motrnutr || '] esta en estado ['
                   || rg_cabecera.motresta || '] y ya no admite articulos nuevos');
         raise pkgerror.generic;
      end if;

      -- Existencia del activo, y de paso su fila completa para las validaciones
      -- de centro de informacion y tercero.
      pkgtbactifijo.prosel(fun_acfiidin(p_motrarti,p_motrplac),null,rg_actifijo);

      pro_valida_activo_tramite(p_motrnutr,p_motrarti,p_motrplac,rg_actifijo);

      pro_insertar_linea(rg_cabecera,p_motrarti,p_motrplac,p_usuario);

      pro_log('Traspaso '|| p_motrnutr || ': agregado el elemento '|| p_motrarti
              || ' placa '|| p_motrplac);

      pkgerror.prounseterror;
   exception
      when pkgerror.generic then
         pkgerror.prounseterror;
         if ( pkgerror.funnivelerror = 0 ) then
            raise_application_error(-20008,pkgerror.funmensaje('GENERIC'));
         else
            raise;
         end if;
      when others then
         pro_log('Linea '|| $$plsql_line|| ': ' || sqlerrm);
         pkgerror.prounseterror;
         if ( pkgerror.funnivelerror = 0 ) then
            raise_application_error(-20008,pkgerror.funmensaje('OTHERS',sqlcode,sqlerrm));
         else
            raise;
         end if;
   end pro_agregar_articulo;


   -----------------------------------------------------------------------------
   -- pro_generar_movimiento
   --
   -- Materializa el traspaso en el inventario del ERP: UNA cabecera en DOCUINVE
   -- y UNA LINEA POR ARTICULO en MOVIINVE, con MOINSECU de 1 a N.
   --
   -- Se invoca UNICAMENTE desde pro_recibir, con las dos firmas ya registradas.
   -- NO al aprobar: aprobar autoriza el tramite, recibir es lo que mueve el
   -- activo. Un traspaso aprobado que nadie firma no deja rastro en inventario.
   --
   -- No lleva control propio de duplicados: pro_recibir solo llega aqui con el
   -- tramite en 'ap' y con sus lineas bloqueadas, de modo que una segunda
   -- recepcion se corta antes por estado.
   --
   -- Los errores se dejan propagar hacia los handlers de pro_recibir, que son
   -- los que arman el RAISE_APPLICATION_ERROR que ve el backend.
   -----------------------------------------------------------------------------
   procedure pro_generar_movimiento ( rg_traspaso in fi_movitras%rowtype,
                                      p_usuario   in varchar2 )
   is
      rg_tidoemnu   tidoemnu%rowtype;
      rg_actifijo   actifijo%rowtype;
      rg_articulo   articulo%rowtype;
      rg_docuinve   docuinve%rowtype;
      rg_moviinve   moviinve%rowtype;
      rg_vacio      moviinve%rowtype;

      sb_estaacti   paraoper.paracova%type;
      sb_estaregi   paraoper.paracova%type;
      sb_tipomovi   tipodocu.tidotimo%type;
      sb_divides    personal.persdivi%type;
      sb_cecodest   persceco.peccceco%type;
      sb_bodedest   bodega.bodecodi%type;
      in_numedocu   docuinve.doinnume%type;
      in_secuencia  number := 0;
      sb_bodelogica varchar2(6);
      sb_concepto   varchar2(10);
   begin
      sb_bodelogica := fun_parametro(c_bodelogica);
      sb_concepto   := fun_parametro(c_conceptomovi);
      sb_estaacti   := fun_parametro(c_estadoactivo);
      sb_estaregi   := fun_parametro(c_estadoregi);

      -------------------------------------------------------------------------
      -- 1) Tipo de movimiento (MOINTIMO)
      -------------------------------------------------------------------------
      begin
         select tidotimo
           into sb_tipomovi
           from tipodocu
          where tidotido = rg_traspaso.motrdrtd;

          pro_log('Tipo de movimiento ' || sb_tipomovi);
      exception
         when no_data_found then
            pro_error('No hay tipo de movimiento asociado al tipo de documento ['
                      || rg_traspaso.motrdrtd || ']');
            raise pkgerror.generic;
      end;

      -------------------------------------------------------------------------
      -- 2) Division, centro de costos y bodega de la persona destino
      --
      -- El documento se emite contra el destino: es quien queda respondiendo por
      -- los elementos cuando el traspaso se materializa.
      -------------------------------------------------------------------------
      begin
         select persdivi
           into sb_divides
           from personal
          where perscodi = rg_traspaso.motrpede;

          pro_log('Division ' || sb_divides);
      exception
         when no_data_found then
            pro_error('La persona destino ['|| rg_traspaso.motrpede
                      || '] no tiene division en PERSONAL');
            raise pkgerror.generic;
      end;

      -- MAX en vez de ROWNUM = 1: una persona puede tener varios centros de
      -- costos activos y hay que elegir uno de forma estable, no el primero que
      -- devuelva el plan de ejecucion.
      select max(peccceco)
        into sb_cecodest
        from persceco
       where peccpers = rg_traspaso.motrpede
         and peccesta = sb_estaacti;

      if ( sb_cecodest is null ) then
         pro_error('La persona destino ['|| rg_traspaso.motrpede
                   || '] no tiene centro de costos activo en PERSCECO');
         raise pkgerror.generic;
      end if;

      sb_bodedest := fun_bodega_persona(rg_traspaso.motrdrem,rg_traspaso.motrpede);

      -------------------------------------------------------------------------
      -- 3) Numeracion oficial del documento de inventario
      --
      -- A diferencia de REQUSUMI, aca el numero no lo pone un trigger: se pide
      -- al numerador del tipo de documento, igual que el resto del ERP.
      -------------------------------------------------------------------------
      pkgtbtidoemnu.prosel(rg_traspaso.motrdrem,rg_traspaso.motrdrtd,null,rg_tidoemnu);

      in_numedocu := pkgdocu_general.funproxi_nume(rg_tidoemnu.tdennume,sb_estaacti);

      pro_log('Numero de documento ' || in_numedocu || ' MOTRDRTD ' || rg_traspaso.motrdrtd);

      -------------------------------------------------------------------------
      -- 4) Cabecera: DOCUINVE, UNA sola aunque el tramite mueva N articulos
      --
      -- DOINTERC sale del activo de la linea de cabecera. Es seguro porque
      -- pro_agregar_articulo obliga a que todos los articulos del tramite
      -- compartan tercero: si no, no habria un valor correcto que poner aqui.
      -------------------------------------------------------------------------
      pkgtbactifijo.prosel(fun_acfiidin(rg_traspaso.motrarti,rg_traspaso.motrplac),null,rg_actifijo);

      rg_docuinve.dointdem := rg_traspaso.motrdrem;
      rg_docuinve.dointido := rg_traspaso.motrdrtd;
      rg_docuinve.doinnume := in_numedocu;
      rg_docuinve.doindiem := rg_traspaso.motrdrem;
      rg_docuinve.doindoem := rg_traspaso.motrdrem;
      rg_docuinve.doinperi := to_char(sysdate,'YYYY');
      rg_docuinve.doinlaps := to_char(sysdate,'MM');
      rg_docuinve.doindocu := c_docuvacio;
      rg_docuinve.doindivi := sb_divides;
      rg_docuinve.doinesta := sb_estaacti;
      rg_docuinve.doinobse := rg_traspaso.motrobse;
      rg_docuinve.doinfech := trunc(sysdate);
      rg_docuinve.doinfesi := trunc(sysdate);
      rg_docuinve.doindigi := p_usuario;
      -- Rastro hacia el tramite: es lo unico que permite volver del documento
      -- del ERP al traspaso que lo origino. Se guarda el NUMERO DE TRAMITE, que
      -- es el identificador de negocio, no el id tecnico de una linea.
      rg_docuinve.doindoso := 'ID TRAMITE SIGAPP: '|| rg_traspaso.motrnutr;
      rg_docuinve.doinvalo := c_cero;
      rg_docuinve.dointerc := rg_actifijo.acfiterc;

      pkgtbdocuinve.proins(rg_docuinve,c_no);

      pro_log('Docuinve ' || rg_docuinve.dointdem || ' dointido ' || rg_docuinve.dointido
              || ' doinnume ' || rg_docuinve.doinnume);

      -------------------------------------------------------------------------
      -- 5) Detalle: MOVIINVE, UNA LINEA POR ARTICULO del tramite
      --
      -- MOINSECU era la constante 1 cuando un traspaso movia un solo elemento.
      -- Ahora es el contador del bucle.
      -------------------------------------------------------------------------
      for rg_linea in ( select motrarti, motrplac
                          from fi_movitras
                         where motrnutr = rg_traspaso.motrnutr
                         order by motridmt ) loop

         pkgtbactifijo.prosel(fun_acfiidin(rg_linea.motrarti,rg_linea.motrplac),null,rg_actifijo);
         pkgtbarticulo.prosel(rg_linea.motrarti,null,rg_articulo);

         in_secuencia := in_secuencia + 1;

         -- Se limpia el registro en cada vuelta: arrastrar valores de la linea
         -- anterior es el error clasico de convertir un insert unico en un bucle.
         rg_moviinve := rg_vacio;

         rg_moviinve.moininem := rg_docuinve.dointdem;
         rg_moviinve.moinintd := rg_docuinve.dointido;
         rg_moviinve.moininnu := rg_docuinve.doinnume;
         rg_moviinve.moinarti := rg_linea.motrarti;
         rg_moviinve.moinsecu := in_secuencia;
         rg_moviinve.moinlaps := rg_docuinve.doinlaps;
         rg_moviinve.moinperi := rg_docuinve.doinperi;
         rg_moviinve.moinfech := rg_docuinve.doinfech;
         rg_moviinve.moinfesi := rg_docuinve.doinfech;
         rg_moviinve.moindivi := rg_docuinve.doindivi;
         rg_moviinve.moindiem := rg_docuinve.dointdem;
         rg_moviinve.moindigi := rg_docuinve.doindigi;
         rg_moviinve.moinesta := sb_estaregi;
         -- Bodega fuente: la que tiene hoy el activo, no la de la persona fuente.
         -- El movimiento tiene que salir de donde el inventario cree que esta.
         rg_moviinve.moinbofu := rg_actifijo.acfibode;
         rg_moviinve.moinbode := sb_bodedest;
         -- Misma bodega logica que se guardo en la requisicion, para que documento
         -- y movimiento no queden contando historias distintas.
         rg_moviinve.moinblfu := sb_bodelogica;
         rg_moviinve.moinblde := sb_bodelogica;
         rg_moviinve.moincant := c_cantidad;
         rg_moviinve.moinfaco := c_facconv;
         rg_moviinve.moinunma := rg_articulo.artiunma;
         -- Todos los costos salen del costo actual del activo: un traspaso cambia
         -- de responsable, no revalua el elemento.
         rg_moviinve.moincost := rg_actifijo.acficoac;
         rg_moviinve.moincpna := rg_actifijo.acficoac;
         rg_moviinve.moincoto := rg_actifijo.acficoac;
         rg_moviinve.moincoaj := rg_actifijo.acficoac;
         rg_moviinve.moincotr := rg_actifijo.acficoac;
         rg_moviinve.moincoiv := c_cero;
         rg_moviinve.moinvave := c_cero;
         rg_moviinve.moinvasa := c_cero;
         rg_moviinve.mointimo := sb_tipomovi;
         rg_moviinve.moinceco := sb_cecodest;
         rg_moviinve.moincomv := sb_concepto;
         rg_moviinve.moindear := rg_actifijo.acfiobse;
         -- Fuente y destino salen del tramite, que es donde quedaron validadas, y
         -- no de ACFIPERS: el tramite es la version aprobada de quien entrega.
         rg_moviinve.moinpefu := rg_traspaso.motrpefu;
         rg_moviinve.moinpede := rg_traspaso.motrpede;
         rg_moviinve.moingere := c_no;
         rg_moviinve.moinplac := rg_linea.motrplac;
         rg_moviinve.mointerc := rg_actifijo.acfiterc;
         rg_moviinve.moinoper := c_operacion;
         rg_moviinve.mointiop := c_operacion;
         rg_moviinve.moinsign := c_no;
         rg_moviinve.moinsetr := rg_actifijo.acfidise;
         rg_moviinve.moincifu := rg_actifijo.acficein;
         rg_moviinve.moincide := rg_actifijo.acficein;
         rg_moviinve.moinmaco := c_si;

         pkgtbmoviinve.proins(rg_moviinve,c_no);

      end loop;

      if ( in_secuencia = 0 ) then
         pro_error('El traspaso ['|| rg_traspaso.motrnutr || '] no tiene articulos');
         raise pkgerror.generic;
      end if;

      pro_log('Traspaso ['|| rg_traspaso.motrnutr || '] genero el documento de inventario ['
              || rg_traspaso.motrdrtd|| '-'|| in_numedocu || '] con '|| in_secuencia || ' linea(s)');
   end pro_generar_movimiento;


   -----------------------------------------------------------------------------
   -- pro_aprobar_rechazar
   -----------------------------------------------------------------------------
   procedure pro_aprobar_rechazar (p_motrnutr   in fi_movitras.motrnutr%type,
                                   p_estado_nvo in fi_movitras.motresta%type,
                                   p_observa    in fi_movitras.motrmore%type,
                                   p_usuario    in fi_movitras.motrusap%type
                                  ) is
      rg_traspaso   fi_movitras%rowtype;
      sb_estadodocu requsumi.resuesta%type;
      in_lineas     number;
   begin
      pkgerror.proseterror('PKG_FI_MOVITRAS.pro_aprobar_rechazar');

      if ( p_estado_nvo not in ( c_aprobado, c_rechazado ) ) then
         pro_error('Estado invalido ['|| p_estado_nvo || ']: solo ap o na');
         raise pkgerror.generic;
      end if;

      if ( p_estado_nvo = c_rechazado and trim(p_observa) is null ) then
         pro_error('Debe indicar el motivo al rechazar el traspaso');
         raise pkgerror.generic;
      end if;

      pro_bloquea_tramite(p_motrnutr,rg_traspaso,in_lineas);

      if ( rg_traspaso.motresta != c_pendiente ) then
         pro_error('El traspaso ['|| p_motrnutr|| '] ya fue procesado, esta en estado ['
                   || rg_traspaso.motresta || ']');
         raise pkgerror.generic;
      end if;

      -- Por MOTRNUTR: el estado se mueve en bloque para las N lineas.
      update fi_movitras
         set motresta = p_estado_nvo,
             motrmore = case when p_estado_nvo = c_rechazado then p_observa else motrmore end,
             motrfeap = systimestamp,
             motrusap = p_usuario,
             motrupat = systimestamp,
             motrupby = p_usuario
       where motrnutr = p_motrnutr;

      -- El documento sigue al tramite: aprobado pasa al estado operativo
      -- configurado, rechazado se anula con la misma clave que usa el ERP.
      -- Ojo: son estados del ERP, no los del tramite.
      if ( p_estado_nvo = c_aprobado ) then
         sb_estadodocu := fun_parametro(c_estadoaprodocu);
      else
         sb_estadodocu := fun_parametro(c_estadoanula);
      end if;

      update requsumi
         set resuesta = sb_estadodocu,
             resufere = sysdate
       where resutdem = rg_traspaso.motrdrem
         and resutido = rg_traspaso.motrdrtd
         and resunume = rg_traspaso.motrdrnd;

      -- EL INVENTARIO NO SE TOCA AQUI.
      --
      -- Aprobar es autorizar el tramite, no mover el activo. El documento de
      -- inventario (DOCUINVE + MOVIINVE) se genera en pro_recibir, cuando las
      -- dos partes ya firmaron y el elemento cambio de manos de verdad. Un
      -- traspaso aprobado que nadie llega a firmar no deja rastro en el
      -- inventario.
      pro_log('Traspaso ' || p_motrnutr || ' con ' || in_lineas
              || ' articulo(s) pasa a estado ' || p_estado_nvo);

      pkgerror.prounseterror;
   exception
      when pkgerror.generic then
         pkgerror.prounseterror;
         if ( pkgerror.funnivelerror = 0 ) then
            raise_application_error( -20008, pkgerror.funmensaje('GENERIC'));
         else
            raise;
         end if;
      when others then
           -- Error no previsto de Oracle: al log con la linea y el SQLERRM, que
           -- es lo unico que despues permite ubicarlo.
         pro_log('Linea '|| $$plsql_line|| ': ' || sqlerrm);
         pkgerror.prounseterror;
         if ( pkgerror.funnivelerror = 0 ) then
            raise_application_error(-20008, pkgerror.funmensaje('OTHERS',sqlcode,sqlerrm));
         else
            raise;
         end if;
   end pro_aprobar_rechazar;


   -----------------------------------------------------------------------------
   -- pro_registrar_firma
   --
   -- Escribe la firma en FI_MOTRFIRM. NO toca FI_MOVITRAS: no hay orden entre
   -- las dos firmas y el tramite se queda en 'ap' hasta que se reciba.
   --
   -- Es un UPDATE y no un INSERT porque las dos filas se crearon vacias al abrir
   -- el tramite. Asi la PK (MOFINUTR, MOFIPOSI) es la que impide fisicamente una
   -- segunda firma sobre la misma posicion, mas alla de la validacion explicita.
   -----------------------------------------------------------------------------
   procedure pro_registrar_firma (p_motrnutr   in fi_movitras.motrnutr%type,
                                  p_tipo_firma in varchar2,
                                  p_firma      in blob,
                                  p_usuario    in varchar2
                                 ) is
      rg_traspaso fi_movitras%rowtype;
      in_lineas   number;
      in_posicion number;
      dt_firmada  fi_motrfirm.mofifefi%type;
   begin
      pkgerror.proseterror('PKG_FI_MOVITRAS.pro_registrar_firma');

      if ( p_tipo_firma = c_firmafuente ) then
         in_posicion := c_posifuente;
      elsif ( p_tipo_firma = c_firmadestino ) then
         in_posicion := c_posidestino;
      else
         pro_error('Tipo de firma invalido ['|| p_tipo_firma || ']: solo FU o DE');
         raise pkgerror.generic;
      end if;

      if ( p_firma is null or dbms_lob.getlength(p_firma) = 0 ) then
         pro_error('La firma llego vacia');
         raise pkgerror.generic;
      end if;

      pro_bloquea_tramite(p_motrnutr,rg_traspaso,in_lineas);

      -- Solo se firma un traspaso aprobado y aun no recibido. No se valida orden
      -- entre fuente y destino: cualquiera puede firmar primero.
      if ( rg_traspaso.motresta != c_aprobado ) then
         pro_error('El traspaso ['|| p_motrnutr || '] no admite firma en estado ['
                   || rg_traspaso.motresta || ']');
         raise pkgerror.generic;
      end if;

      begin
         select mofifefi
           into dt_firmada
           from fi_motrfirm
          where mofinutr = p_motrnutr
            and mofiposi = in_posicion
            for update;
      exception
         when no_data_found then
            pro_error('El traspaso ['|| p_motrnutr
                      || '] no tiene creada la posicion de firma ['|| in_posicion || ']');
            raise pkgerror.generic;
      end;

      if ( dt_firmada is not null ) then
         pro_error('La firma ['|| p_tipo_firma || '] del traspaso ['|| p_motrnutr
                   || '] ya fue registrada');
         raise pkgerror.generic;
      end if;

      -- Fecha e imagen se escriben juntas: es la coherencia que no se pudo
      -- declarar como CHECK, porque Oracle restringe las columnas LOB en
      -- constraints.
      update fi_motrfirm
         set mofifefi = systimestamp,
             mofifipe = p_firma,
             mofiupat = systimestamp,
             mofiupby = p_usuario
       where mofinutr = p_motrnutr
         and mofiposi = in_posicion;

      pkgerror.prounseterror;
   exception
      when pkgerror.generic then
         pkgerror.prounseterror;
         if ( pkgerror.funnivelerror = 0 ) then
            raise_application_error( -20008, pkgerror.funmensaje('GENERIC'));
         else
            raise;
         end if;
      when others then
         pro_log('Linea '|| $$plsql_line|| ': ' || sqlerrm);
         pkgerror.prounseterror;
         if ( pkgerror.funnivelerror = 0 ) then
            raise_application_error(-20008, pkgerror.funmensaje('OTHERS', sqlcode, sqlerrm));
         else
            raise;
         end if;
   end pro_registrar_firma;


   -----------------------------------------------------------------------------
   -- pro_recibir
   -----------------------------------------------------------------------------
   procedure pro_recibir (p_motrnutr in fi_movitras.motrnutr%type,
                          p_usuario  in varchar2
                         ) is
      rg_traspaso  fi_movitras%rowtype;
      in_lineas    number;
      in_sinfirmar number;
   begin
      pkgerror.proseterror('PKG_FI_MOVITRAS.pro_recibir');

      pro_bloquea_tramite(p_motrnutr,rg_traspaso,in_lineas);

      if ( rg_traspaso.motresta = c_recibido ) then
         pro_error('El traspaso ['|| p_motrnutr || '] ya fue recibido');
         raise pkgerror.generic;
      end if;

      if ( rg_traspaso.motresta != c_aprobado ) then
         pro_error('El traspaso ['|| p_motrnutr || '] no se puede recibir en estado ['
                   || rg_traspaso.motresta || ']');
         raise pkgerror.generic;
      end if;

      -- Las dos firmas, en cualquier orden: lo unico que importa es que ninguna
      -- posicion quede sin fecha.
      select count(1)
        into in_sinfirmar
        from fi_motrfirm
       where mofinutr = p_motrnutr
         and mofifefi is null;

      if ( in_sinfirmar > 0 ) then
         pro_error('El traspaso ['|| p_motrnutr
                   || '] requiere la firma de ambas partes antes de recibirse');
         raise pkgerror.generic;
      end if;

      -------------------------------------------------------------------------
      -- Documento de inventario
      --
      -- REGLA DE ORO DEL MODULO: DOCUINVE y MOVIINVE se generan AQUI, nunca al
      -- aprobar. El movimiento de inventario refleja que el elemento cambio de
      -- manos, y eso solo es cierto cuando las dos partes firmaron, cosa que se
      -- acaba de validar arriba.
      --
      -- Va en la misma transaccion que el cambio de estado a proposito: o queda
      -- el traspaso recibido CON su documento de inventario completo -las N
      -- lineas-, o no queda nada.
      -------------------------------------------------------------------------
      pro_log('Recibiendo traspaso ' || p_motrnutr || ' con ' || in_lineas
              || ' articulo(s): pro_generar_movimiento');

      rg_traspaso.motresta := c_recibido;
      pro_generar_movimiento(rg_traspaso,p_usuario);

      update fi_movitras
         set motrfere = systimestamp,
             motresta = c_recibido,
             motrupat = systimestamp,
             motrupby = p_usuario
       where motrnutr = p_motrnutr;

      -------------------------------------------------------------------------
      -- Punto de enganche con el ERP
      --
      -- Aqui va el cambio de responsable en ACTIFIJO (ACFIPERS / ACFIBODE), que
      -- acompana al movimiento de inventario recien generado. Con varios
      -- articulos por tramite ya no es una linea sino un bucle sobre las lineas
      -- del MOTRNUTR. Se deja marcado en vez de inventado porque depende del
      -- concepto de movimiento configurado, que es justamente lo que se esta
      -- parametrizando.
      --
      --   for rg_linea in ( select motrarti, motrplac
      --                       from fi_movitras
      --                      where motrnutr = p_motrnutr ) loop
      --      ...
      --   end loop;
      -------------------------------------------------------------------------

      pkgerror.prounseterror;
   exception
      when pkgerror.generic then
         pkgerror.prounseterror;
         if ( pkgerror.funnivelerror = 0 ) then
            raise_application_error( -20008, pkgerror.funmensaje('GENERIC'));
         else
            raise;
         end if;
      when others then
         pro_log('Linea '|| $$plsql_line|| ': ' || sqlerrm);
         pkgerror.prounseterror;
         if ( pkgerror.funnivelerror = 0 ) then
            raise_application_error( -20008, pkgerror.funmensaje( 'OTHERS', sqlcode, sqlerrm));
         else
            raise;
         end if;
   end pro_recibir;

end pkg_fi_movitras;
/
