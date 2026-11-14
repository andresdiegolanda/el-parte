-- Generado desde docs/base-de-afirmaciones.md: el documento manda y el verificador controla que coincidan.
-- No se edita a mano: se cambia el documento y se vuelve a copiar el bloque.

create schema parte;
create schema cuentas;

-- Fuentes: una fila por fuente de la versión 0 (ADR 0006).
create table parte.fuentes (
    id             text primary key,                 -- 'arce', 'bid', 'banco-mundial', 'presidencia'
    nombre         text not null,
    url            text not null,
    licencia       text not null,
    verificada_en  date not null,
    activa         boolean not null default true
);

-- Entidades a las que alguien se puede suscribir. Nunca personas físicas (ADR 0008).
create table parte.entidades (
    id                text primary key,              -- por ejemplo 'organismo:bps'
    tipo              text not null check (tipo in ('organismo', 'persona_juridica', 'programa', 'licitacion', 'proyecto')),
    nombre            text not null,
    identificadores   jsonb not null default '{}',   -- RUT, códigos en cada fuente
    creada_en         timestamptz not null default now()
);

-- Corridas y su libro de gasto (ADR 0005).
create table parte.corridas (
    id                    text primary key,          -- por ejemplo 'c-2026-11-06-normal'
    modo                  text not null check (modo in ('normal', 'reproduccion', 'banco')),
    fecha_objetivo        date not null,
    estado                text not null check (estado in ('en_curso', 'completa', 'cortada', 'fallida')),
    limite_alcanzado      text check (limite_alcanzado in ('costo', 'pasos', 'tiempo')),
    presupuesto_usd       numeric(10, 4) not null,
    presupuesto_pasos     integer not null,
    presupuesto_minutos   integer not null,
    modelos               jsonb not null,            -- el modelo de cada agente, congelado al empezar (ADR 0003)
    iniciada_en           timestamptz not null,
    terminada_en          timestamptz
);

create table parte.libros_de_corrida (
    corrida_id      text primary key references parte.corridas (id),
    gastado_usd     numeric(10, 6) not null default 0,
    reservado_usd   numeric(10, 6) not null default 0,
    pasos           integer not null default 0,
    actualizado_en  timestamptz not null default now()
);

-- Modelo de cada agente (ADR 0003). Un cambio es una fila nueva: el vigente es la más nueva de cada agente.
create table parte.modelos_de_agente (
    id        bigint generated always as identity primary key,
    agente    text not null,                    -- 'explorador-arce', 'archivista', 'refutador', ...
    modelo    text not null,                    -- por ejemplo 'claude-sonnet-5-5'
    desde     timestamptz not null default now(),
    unique (agente, desde)
);

-- Precio de cada modelo, con su fuente (ADR 0005). También se cambia con una fila nueva.
create table parte.precios_de_modelo (
    id                          bigint generated always as identity primary key,
    modelo                      text not null,
    usd_millon_entrada          numeric(10, 4) not null,
    usd_millon_salida           numeric(10, 4) not null,
    usd_millon_cache_lectura    numeric(10, 4) not null,
    usd_millon_cache_escritura  numeric(10, 4) not null,
    fuente_url                  text not null,
    consultado_el               date not null,
    desde                       timestamptz not null default now(),
    unique (modelo, desde)
);

-- Registro de mensajes: lo escribe el observador (ADR 0002). La sala de máquinas sale de acá.
create table parte.mensajes (
    mensaje_id   text primary key,
    tipo         text not null,
    version      integer not null,
    corrida_id   text not null references parte.corridas (id),
    causa_id     text,
    agente       text not null,
    emitido_en   timestamptz not null,
    contenido    jsonb not null
);
create index mensajes_por_corrida on parte.mensajes (corrida_id, emitido_en);
create index mensajes_por_causa on parte.mensajes (causa_id);

-- Lo que procesó cada agente, para la idempotencia (docs/contratos.md).
create table parte.mensajes_procesados (
    consumidor    text not null,
    mensaje_id    text not null,
    procesado_en  timestamptz not null default now(),
    primary key (consumidor, mensaje_id)
);

-- Lo que se leyó de las fuentes y lo que respondió el modelo en cada corrida, ya enmascarado,
-- para repetirla sin red ni costo (modo reproducción, docs/contratos.md).
create table parte.grabaciones (
    corrida_id   text not null references parte.corridas (id),
    clave        text not null,                      -- qué se pidió, por ejemplo 'arce:publicacion:<id>'
    tipo         text not null check (tipo in ('fuente', 'modelo')),
    contenido    jsonb not null,
    grabada_en   timestamptz not null default now(),
    primary key (corrida_id, clave)
);

-- Hechos: la identidad. Su contenido está en las versiones.
create table parte.hechos (
    id               bigint generated always as identity primary key,
    clave_de_hecho   text not null unique,           -- fuente:identificador:tipoDeHecho
    fuente_id        text not null references parte.fuentes (id),
    tipo_de_hecho    text not null,
    creado_en        timestamptz not null default now()
);

create table parte.hecho_entidades (
    hecho_id    bigint not null references parte.hechos (id),
    entidad_id  text not null references parte.entidades (id),
    rol         text not null,                       -- comprador, proveedor, prestatario...
    primary key (hecho_id, entidad_id, rol)
);

-- Cada versión de un hecho, con su grado y la traza de los agentes que la produjeron.
create table parte.versiones_de_hecho (
    id                   bigint generated always as identity primary key,
    hecho_id             bigint not null references parte.hechos (id),
    numero               integer not null,
    afirmacion           text not null,
    grado                text not null check (grado in ('PROVEN', 'INFERRED')),
    razonamiento         text,
    fecha_publicacion    text not null,              -- como la da la fuente
    fecha_confirmada     boolean not null,
    que_dice             text not null,
    por_que_importa      text not null,
    que_mirar            text not null,
    estado_revision      text not null check (estado_revision in ('no_requiere', 'pendiente', 'aceptado', 'rechazado')),
    corrida_id           text not null references parte.corridas (id),
    mensaje_candidato    text not null references parte.mensajes (mensaje_id),
    mensaje_cotejado     text not null references parte.mensajes (mensaje_id),
    mensaje_graduado     text not null references parte.mensajes (mensaje_id),
    mensaje_analizado    text not null references parte.mensajes (mensaje_id),
    creada_en            timestamptz not null default now(),
    unique (hecho_id, numero),
    check (grado <> 'INFERRED' or razonamiento is not null)
);

create table parte.evidencias (
    id               bigint generated always as identity primary key,
    version_id       bigint not null references parte.versiones_de_hecho (id),
    url              text not null,
    fragmento        text not null,                  -- ya enmascarado (ADR 0008)
    hash_contenido   text
);

create table parte.revisiones (
    version_id    bigint primary key references parte.versiones_de_hecho (id),
    estado        text not null check (estado in ('aceptado', 'rechazado')),
    nota          text,
    revisada_en   timestamptz not null default now()
);

-- Clientes: identificador opaco y nada más (ADR 0008).
create table parte.clientes (
    cliente_id   uuid primary key,
    alias        text not null,                      -- por ejemplo 'Cliente 2' o 'Demostración'
    es_demo      boolean not null default false
);

create table parte.suscripciones (
    cliente_id   uuid not null references parte.clientes (cliente_id),
    entidad_id   text not null references parte.entidades (id),
    creada_en    timestamptz not null default now(),
    primary key (cliente_id, entidad_id)
);

create table parte.informes (
    id                bigint generated always as identity primary key,
    cliente_id        uuid not null references parte.clientes (cliente_id),
    fecha_objetivo    date not null,
    corrida_id        text not null references parte.corridas (id),
    sin_novedades     boolean not null,
    corrida_cortada   boolean not null,
    creado_en         timestamptz not null default now(),
    unique (cliente_id, fecha_objetivo, corrida_id)
);

create table parte.informe_hechos (
    informe_id   bigint not null references parte.informes (id),
    version_id   bigint not null references parte.versiones_de_hecho (id),
    primary key (informe_id, version_id)
);

-- Esquema aparte: los correos (ADR 0008). Los tokens del login se guardan como los defina
-- la implementación de Spring Security que se use, también en este esquema.
create table cuentas.usuarios (
    cliente_id   uuid primary key,                   -- el mismo identificador que parte.clientes
    correo       text not null unique,
    creado_en    timestamptz not null default now()
);
