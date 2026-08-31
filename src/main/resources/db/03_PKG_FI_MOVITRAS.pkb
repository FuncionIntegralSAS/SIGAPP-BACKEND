create or replace package body pkg_fi_movitras is

   -----------------------------------------------------------------------------
   -- COMPILAR EN EL ESQUEMA DE RECURSOS FISICOS SRF (SRFPNACUA), dueno de
   -- FI_MOVITRAS y del que dependen sin calificar las tablas del ERP que se usan
   -- aqui (REQUSUMI, ACTIFIJO, PARAOPER, TIMOOPER, TIPODOCU, DOCUINVE, MOVIINVE,
   -- ARTICULO, PERSCECO, TIDOEMNU).
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
   -- Estado operativo al que pasa el documento cuando el traspaso se aprueba.
   c_estadoaprueba  varchar2(10) := 'ESTAAPRO';
   -- Estado con el que el ERP marca un documento anulado. Misma clave que usa
   -- TRGBUPT_REQUSUMI, para no inventar una segunda fuente de verdad.
   c_estadoanula   varchar2(10) := 'ESTAANUL';

   -- Operacion que el tipo de movimiento debe tener habilitada en TIMOOPER para
   -- poder usarse en un traspaso. Es tambien la que queda marcada en el
   -- movimiento de inventario (MOINOPER / MOINTIOP).
   c_operacion     varchar2(20) := 'TRASPASO';

   -- Valores fijos del movimiento de inventario de un traspaso: se mueve una
   -- unidad del elemento, sin factor de conversion ni valores agregados.
   c_cantidad      number := 1;
   c_secuencia     number := 1;
   c_facconv       number := 1;
   c_cero          number := 0;
   c_no            varchar2(1) := 'N';
   c_si            varchar2(1) := 'S';
   c_docuvacio     varchar2(1) := '.';

   -----------------------------------------------------------------------------
   -- Estados de FI_MOVITRAS
   -----------------------------------------------------------------------------
   c_pendiente     varchar2(2) := 'pe';
   c_rechazado     varchar2(2) := 'na';
   c_acepfuente    varchar2(2) := 'af';
   c_acepdestino   varchar2(2) := 'ad';
   c_recibido      varchar2(2) := 're';
   c_firmafuente   varchar2(2) := 'FU';
   c_firmadestino  varchar2(2) := 'DE';

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
         and nvl(acfiplac,'.') = nvl(p_placa,'.');

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
                                  p_motridmt  out fi_movitras.motridmt%type,
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
      -- traspasos al mismo centro.
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
      -- 2) Traspaso, ya con el documento existente
      -------------------------------------------------------------------------
      p_motridmt := fi_movitras_seq.nextval;
      insert into fi_movitras (
         motridmt,
         motrdrem,
         motrdrtd,
         motrdrnd,
         motrpefu,
         motrpede,
         motrarti,
         motrplac,
         motresta,
         motrobse,
         motrfecr,
         motruscr,
         motrcrby
      ) values ( p_motridmt,
                 p_motrdrem,
                 sb_tipodocu,
                 in_numedocu,
                 p_motrpefu,
                 p_motrpede,
                 p_motrarti,
                 p_motrplac,
                 c_pendiente,
                 p_motrobse,
                 systimestamp,
                 p_motruscr,
                 p_motruscr );

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
   -- pro_generar_movimiento
   --
   -- Materializa el traspaso en el inventario del ERP: cabecera en DOCUINVE y
   -- detalle en MOVIINVE, una sola linea porque un traspaso mueve un elemento.
   --
   -- Se invoca UNICAMENTE al aprobar. Al rechazar no hay movimiento que
   -- registrar: el documento REQUSUMI se anula y el tramite muere ahi.
   --
   -- No lleva control propio de duplicados: pro_aprobar_rechazar solo llega aqui
   -- con el tramite en 'pe' y bajo el FOR UPDATE de la fila, de modo que una
   -- segunda aprobacion se corta antes por estado.
   --
   -- Los errores se dejan propagar hacia los handlers de pro_aprobar_rechazar,
   -- que son los que arman el RAISE_APPLICATION_ERROR que ve el backend.
   -----------------------------------------------------------------------------
   procedure pro_generar_movimiento ( rg_traspaso in fi_movitras%rowtype,
                                      p_usuario   in varchar2 )
   is
      rg_tidoemnu   tidoemnu%rowtype;
      rg_actifijo   actifijo%rowtype;
      rg_articulo   articulo%rowtype;
      rg_docuinve   docuinve%rowtype;
      rg_moviinve   moviinve%rowtype;

      sb_estaacti   paraoper.paracova%type;
      sb_estaregi   paraoper.paracova%type;
      sb_concmovi   paraoper.paracova%type;
      sb_tipomovi   tipodocu.tidotimo%type;
      sb_divides    personal.persdivi%type;
      sb_cecodest   persceco.peccceco%type;
      sb_bodedest   bodega.bodecodi%type;
      in_numedocu   docuinve.doinnume%type;
      sb_bodelogica varchar2(6);
      sb_concepto   varchar2(10);
   begin
      sb_bodelogica := fun_parametro(c_bodelogica);
      sb_concepto   := fun_parametro(c_conceptomovi);
      sb_estaacti   := fun_parametro(c_estadoactivo);
      sb_estaregi   := fun_parametro(c_estadoregi);

      -------------------------------------------------------------------------
      -- 1) Datos del activo y de su articulo
      -------------------------------------------------------------------------
      pkgtbactifijo.prosel(fun_acfiidin(rg_traspaso.motrarti,rg_traspaso.motrplac),null,rg_actifijo);

      pkgtbarticulo.prosel(rg_traspaso.motrarti,null,rg_articulo);

      -------------------------------------------------------------------------
      -- 2) Tipo de movimiento (MOINTIMO)
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
      -- 3) Division, centro de costos y bodega de la persona destino
      --
      -- El documento se emite contra el destino: es quien queda respondiendo por
      -- el elemento cuando el traspaso se materializa.
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
      -- 4) Numeracion oficial del documento de inventario
      --
      -- A diferencia de REQUSUMI, aca el numero no lo pone un trigger: se pide
      -- al numerador del tipo de documento, igual que el resto del ERP.
      -------------------------------------------------------------------------
      pkgtbtidoemnu.prosel(rg_traspaso.motrdrem,rg_traspaso.motrdrtd,null,rg_tidoemnu);

      in_numedocu := pkgdocu_general.funproxi_nume(rg_tidoemnu.tdennume,sb_estaacti);

      pro_log('Numero de documento ' || in_numedocu || ' MOTRDRTD ' || rg_traspaso.motrdrtd);
      -------------------------------------------------------------------------
      -- 5) Cabecera: DOCUINVE
      -------------------------------------------------------------------------
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
      -- del ERP al traspaso que lo origino.
      rg_docuinve.doindoso := 'ID TRAMITE SIGAPP: '|| rg_traspaso.motridmt;
      rg_docuinve.doinvalo := c_cero;
      rg_docuinve.dointerc := rg_actifijo.acfiterc;

      pkgtbdocuinve.proins(rg_docuinve,c_no);

      pro_log('Docuinve ' || rg_docuinve.dointdem || ' dointido ' || rg_docuinve.dointido || ' doinnume ' || rg_docuinve.doinnume);

      -------------------------------------------------------------------------
      -- 6) Detalle: MOVIINVE
      -------------------------------------------------------------------------
      rg_moviinve.moininem := rg_docuinve.dointdem;
      rg_moviinve.moinintd := rg_docuinve.dointido;
      rg_moviinve.moininnu := rg_docuinve.doinnume;
      rg_moviinve.moinarti := rg_traspaso.motrarti;
      rg_moviinve.moinsecu := c_secuencia;
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
      -- Tods los costos salen del costo actual del activo: un traspaso cambia de responsable, no revalua el elemento.
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
      rg_moviinve.moinplac := rg_traspaso.motrplac;
      rg_moviinve.mointerc := rg_actifijo.acfiterc;
      rg_moviinve.moinoper := c_operacion;
      rg_moviinve.mointiop := c_operacion;
      rg_moviinve.moinsign := c_no;
      rg_moviinve.moinsetr := rg_actifijo.acfidise;
      rg_moviinve.moincifu := rg_actifijo.acficein;
      rg_moviinve.moincide := rg_actifijo.acficein;
      rg_moviinve.moinmaco := c_si;

      pro_log('rg_docuinve.dointido ' || rg_docuinve.dointido || ' rg_moviinve.mointimo ' || rg_moviinve.mointimo);
      
      pkgtbmoviinve.proins(rg_moviinve,c_no);

      pro_log('Traspaso ['|| rg_traspaso.motridmt || '] genero el documento de inventario ['
              || rg_traspaso.motrdrtd|| '-'|| in_numedocu || ']');
   end pro_generar_movimiento;


   -----------------------------------------------------------------------------
   -- pro_aprobar_rechazar
   -----------------------------------------------------------------------------
   procedure pro_aprobar_rechazar (p_motridmt   in fi_movitras.motridmt%type,
                                   p_estado_nvo in fi_movitras.motresta%type,
                                   p_observa    in fi_movitras.motrmore%type,
                                   p_usuario    in fi_movitras.motrusap%type
                                  ) is
      rg_traspaso   fi_movitras%rowtype;
      sb_estadodocu requsumi.resuesta%type;
   begin
      c_estadoaprueba := fun_parametro('ESTAAPRO');
      pkgerror.proseterror('PKG_FI_MOVITRAS.pro_aprobar_rechazar');

      if ( p_estado_nvo not in ( c_estadoaprueba, c_rechazado ) ) then
         pro_error('Estado invalido ['|| p_estado_nvo || ']: solo ap o na');
         raise pkgerror.generic;
      end if;

      if ( p_estado_nvo = c_rechazado and trim(p_observa) is null ) then
         pro_error('Debe indicar el motivo al rechazar el traspaso');
         raise pkgerror.generic;
      end if;

      begin
         select *
           into rg_traspaso
           from fi_movitras
          where motridmt = p_motridmt;
      exception
         when no_data_found then
            pro_error('No existe el traspaso ['|| p_motridmt || ']');
            raise pkgerror.generic;
      end;

      if ( rg_traspaso.motresta != c_pendiente ) then
         pro_error('El traspaso ['|| p_motridmt|| '] ya fue procesado, 
                    esta en estado ['|| rg_traspaso.motresta || ']');
         raise pkgerror.generic;
      end if;

      update fi_movitras
         set motresta = p_estado_nvo,
             motrmore = case when p_estado_nvo = c_rechazado then p_observa else motrmore end,
             motrfeap = systimestamp,
             motrusap = p_usuario,
             motrupat = systimestamp,
             motrupby = p_usuario
       where motridmt = p_motridmt;

      -- El documento sigue al tramite: aprobado pasa al estado operativo
      -- configurado, rechazado se anula con la misma clave que usa el ERP.
      if ( p_estado_nvo = c_estadoaprueba ) then
         sb_estadodocu := c_estadoaprueba;
      else
         sb_estadodocu := fun_parametro(c_estadoanula);
      end if;

      update requsumi
         set resuesta = sb_estadodocu,
             resufere = sysdate
       where resutdem = rg_traspaso.motrdrem
         and resutido = rg_traspaso.motrdrtd
         and resunume = rg_traspaso.motrdrnd;

      -- Solo la aprobacion llega al inventario. El rechazo se queda en el
      -- cambio de estado: el documento anulado no genera movimiento.
      --
      -- Va en la misma transaccion que los UPDATE de arriba a proposito: o queda
      -- el tramite aprobado con su documento de inventario, o no queda nada.
      if ( p_estado_nvo = c_estadoaprueba ) then
         pro_log('Procesando traspaso ' || p_motridmt || ' con estado ' || p_estado_nvo|| ' pro_generar_movimiento');
         pro_generar_movimiento(rg_traspaso,p_usuario);
      end if;

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
   -----------------------------------------------------------------------------
   procedure pro_registrar_firma (p_motridmt   in fi_movitras.motridmt%type,
                                  p_tipo_firma in varchar2,
                                  p_firma      in blob,
                                  p_usuario    in varchar2
                                 ) is
      rg_traspaso fi_movitras%rowtype;
   begin
      c_estadoaprueba := fun_parametro('ESTAAPRO');
      pkgerror.proseterror('PKG_FI_MOVITRAS.pro_registrar_firma');
      if ( p_tipo_firma not in ( c_firmafuente, c_firmadestino ) ) then
         pro_error('Tipo de firma invalido ['|| p_tipo_firma || ']: solo FU o DE');
         raise pkgerror.generic;
      end if;

      if ( p_firma is null or dbms_lob.getlength(p_firma) = 0 ) then
         pro_error('La firma llego vacia');
         raise pkgerror.generic;
      end if;

      begin
         select *
           into rg_traspaso
           from fi_movitras
          where motridmt = p_motridmt
         for update;
      exception
         when no_data_found then
            pro_error('No existe el traspaso ['|| p_motridmt || ']');
            raise pkgerror.generic;
      end;

      -- Solo se firma un traspaso aprobado y aun no recibido.
      if ( rg_traspaso.motresta not in ( c_estadoaprueba, c_acepfuente, c_acepdestino ) ) then
         pro_error('El traspaso ['|| p_motridmt || '] no admite firma en estado ['|| rg_traspaso.motresta || ']');
         raise pkgerror.generic;
      end if;

      if ( p_tipo_firma = c_firmafuente ) then
         if ( rg_traspaso.motrfafu is not null ) then
            pro_error('La persona fuente ya firmo el traspaso ['|| p_motridmt || ']');
            raise pkgerror.generic;
         end if;

         update fi_movitras
            set motrfifu = p_firma,
                motrfafu = systimestamp,
                motresta = c_acepfuente,
                motrupat = systimestamp,
                motrupby = p_usuario
          where motridmt = p_motridmt;
      else
         if ( rg_traspaso.motrfade is not null ) then
            pro_error('La persona destino ya firmo el traspaso ['|| p_motridmt || ']');
            raise pkgerror.generic;
         end if;

         -- El destino solo firma despues de la fuente: es la fuente quien
         -- entrega el elemento.
         if ( rg_traspaso.motrfafu is null ) then
            pro_error('La persona fuente aun no ha firmado el traspaso ['
                      || p_motridmt || ']');
            raise pkgerror.generic;
         end if;

         update fi_movitras
            set motrfide = p_firma,
                motrfade = systimestamp,
                motresta = c_acepdestino,
                motrupat = systimestamp,
                motrupby = p_usuario
          where motridmt = p_motridmt;
      end if;

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
            raise_application_error(-20008, pkgerror.funmensaje('OTHERS', sqlcode, sqlerrm));
         else
            raise;
         end if;
   end pro_registrar_firma;


   -----------------------------------------------------------------------------
   -- pro_recibir
   -----------------------------------------------------------------------------
   procedure pro_recibir (p_motridmt in fi_movitras.motridmt%type,
                          p_usuario  in varchar2
                         ) is
      rg_traspaso fi_movitras%rowtype;
   begin
      pkgerror.proseterror('PKG_FI_MOVITRAS.pro_recibir');
      begin
         select *
           into rg_traspaso
           from fi_movitras
          where motridmt = p_motridmt
         for update;
      exception
         when no_data_found then
            pro_error('No existe el traspaso ['|| p_motridmt || ']');
            raise pkgerror.generic;
      end;

      if ( rg_traspaso.motresta = c_recibido ) then
         pro_error('El traspaso ['|| p_motridmt || '] ya fue recibido');
         raise pkgerror.generic;
      end if;

      if ( rg_traspaso.motrfafu is null
      or rg_traspaso.motrfade is null ) then
         pro_error('El traspaso ['|| p_motridmt || '] requiere la firma de ambas partes antes de recibirse');
         raise pkgerror.generic;
      end if;

      update fi_movitras
         set motrfere = systimestamp,
             motresta = c_recibido,
             motrupat = systimestamp,
             motrupby = p_usuario
       where motridmt = p_motridmt;

      -------------------------------------------------------------------------
      -- Punto de enganche con el ERP
      --
      -- Aqui va el traslado real del activo (cambio de responsable en ACTIFIJO y
      -- generacion del movimiento de inventario). Se deja marcado en vez de
      -- inventado porque depende del concepto de movimiento configurado, que es
      -- justamente lo que se esta parametrizando.
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
           -- Error no previsto de Oracle: al log con la linea y el SQLERRM, que
           -- es lo unico que despues permite ubicarlo.
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

