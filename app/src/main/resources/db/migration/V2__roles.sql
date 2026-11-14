-- Dos roles de base, uno por esquema (docs/base-de-afirmaciones.md, ADR 0008):
-- el de los agentes y la web no ve `cuentas`, y el del login no ve `parte`.
-- Las claves llegan como parámetros de Flyway desde la configuración, nunca desde el repositorio.

do $$
begin
    if not exists (select from pg_roles where rolname = '${rol_agentes}') then
        execute format('create role %I login password %L', '${rol_agentes}', '${clave_agentes}');
    end if;
    if not exists (select from pg_roles where rolname = '${rol_cuentas}') then
        execute format('create role %I login password %L', '${rol_cuentas}', '${clave_cuentas}');
    end if;
end
$$;

revoke all on schema parte from public;
revoke all on schema cuentas from public;

grant usage on schema parte to ${rol_agentes};
grant select, insert, update, delete on all tables in schema parte to ${rol_agentes};
grant usage, select on all sequences in schema parte to ${rol_agentes};
alter default privileges in schema parte grant select, insert, update, delete on tables to ${rol_agentes};
alter default privileges in schema parte grant usage, select on sequences to ${rol_agentes};

grant usage on schema cuentas to ${rol_cuentas};
grant select, insert, update, delete on all tables in schema cuentas to ${rol_cuentas};
alter default privileges in schema cuentas grant select, insert, update, delete on tables to ${rol_cuentas};
