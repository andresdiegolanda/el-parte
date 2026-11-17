-- El aviso diario del parte (T35, ADR 0013): un envío por invitado y por noche, para no mandar dos veces
-- el aviso de la misma noche aunque se corra de nuevo. En el esquema cuentas, con el rol de las cuentas.
create table cuentas.envios_del_parte (
    cliente_id      uuid not null references cuentas.usuarios (cliente_id),
    fecha_objetivo  date not null,
    corrida_id      text not null,
    enviado_en      timestamptz not null default now(),
    primary key (cliente_id, fecha_objetivo)
);
