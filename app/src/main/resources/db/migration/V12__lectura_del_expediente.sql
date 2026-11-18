-- La lectura del expediente con contexto largo (ADR 0018, T47): una corrida aparte, en modo lectura, que
-- pide el responsable. El modelo propone observaciones que citan hechos, y el refutador las gradúa contra
-- esos hechos. Se guardan la lectura y las observaciones que se sostienen; las descartadas quedan en los
-- mensajes de la corrida.
alter table parte.corridas drop constraint if exists corridas_modo_check;
alter table parte.corridas add constraint corridas_modo_check
    check (modo in ('normal', 'reproduccion', 'banco', 'lectura'));

create table parte.lecturas_de_expediente (
    corrida_id          text primary key references parte.corridas (id),
    entidad_id          text not null references parte.entidades (id),
    desde               date not null,
    hasta               date not null,
    hechos_leidos       integer,
    hechos_cortados     integer,
    huella_de_los_hechos text,
    propuestas          integer,
    descartadas         integer,
    check (desde <= hasta)
);

create table parte.observaciones (
    id            bigserial primary key,
    corrida_id    text not null references parte.lecturas_de_expediente (corrida_id),
    numero        integer not null,
    clase         text not null check (clase in ('repeticion', 'cadena', 'proveedor', 'otra')),
    texto         text not null,
    grado         text not null check (grado in ('PROVEN', 'INFERRED')),
    razonamiento  text,
    hechos        text[] not null check (cardinality(hechos) >= 2),
    unique (corrida_id, numero),
    check (grado <> 'INFERRED' or razonamiento is not null)
);

create index observaciones_de_la_lectura on parte.observaciones (corrida_id);

-- El lector de expedientes usa el mismo modelo que el refutador (ADR 0018, "Costos").
insert into parte.modelos_de_agente (agente, modelo, desde) values
    ('lector-de-expedientes', 'claude-sonnet-5-5', timestamptz '2026-11-18 00:00:00+00');
