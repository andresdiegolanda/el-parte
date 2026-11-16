-- El login por enlace de correo (ADR 0013, T25). Todo en el esquema cuentas, que el rol de los agentes
-- no ve. Las dos tablas de Spring Security llevan sus nombres y columnas de siempre: las clases que las
-- usan las nombran sin esquema, y el rol de las cuentas entra con search_path = cuentas.

-- Quién es el responsable del proyecto, y si el invitado sigue en la lista de invitados.
alter table cuentas.usuarios
    add column responsable boolean not null default false,
    add column activo boolean not null default true;

-- Los tokens de un solo uso (JdbcOneTimeTokenService). El nombre de usuario es el correo.
create table cuentas.one_time_tokens (
    token_value  varchar(36) primary key,
    username     text not null,
    expires_at   timestamp not null
);

-- Los recordatorios de sesión (JdbcTokenRepositoryImpl), por 30 días.
create table cuentas.persistent_logins (
    username   text not null,
    series     varchar(64) primary key,
    token      varchar(64) not null,
    last_used  timestamp not null
);
