-- El perfil (T34, ADR 0013): si cada invitado quiere el aviso diario por correo, y el catálogo inicial de
-- lo que se puede seguir. Nada de esto es un dato personal.

-- El aviso diario (T35) sale para quien lo quiere; un invitado nuevo lo tiene prendido y lo apaga en su
-- perfil.
alter table cuentas.usuarios add column aviso_diario boolean not null default true;

-- Organismos con su código de comprador en ARCE: el código y el nombre se leyeron en una publicación de
-- cada uno el 2026-11-16 (docs/como-se-hizo/perfil.md). Se sigue lo que el explorador puede filtrar: el
-- comprador de cada publicación.
insert into parte.entidades (id, tipo, nombre, identificadores) values
    ('organismo:ose', 'organismo', 'Administración de las Obras Sanitarias del Estado (OSE)', '{"arce": ["66-1"]}'),
    ('organismo:ute', 'organismo', 'Administración Nacional de Usinas y Trasmisiones Eléctricas (UTE)', '{"arce": ["61-1"]}'),
    ('organismo:antel', 'organismo', 'Administración Nacional de Telecomunicaciones (ANTEL)', '{"arce": ["65-1"]}'),
    ('organismo:ancap', 'organismo', 'Administración Nacional de Combustible, Alcohol y Portland (ANCAP)', '{"arce": ["60-1"]}'),
    ('organismo:bcu', 'organismo', 'Banco Central del Uruguay', '{"arce": ["50-1"]}'),
    ('organismo:inau', 'organismo', 'Instituto del Niño y Adolescente del Uruguay (INAU)', '{"arce": ["27-1"]}'),
    ('organismo:utec', 'organismo', 'Universidad Tecnológica del Uruguay (UTEC)', '{"arce": ["31-1"]}'),
    ('organismo:poder-judicial', 'organismo', 'Poder Judicial', '{"arce": ["16-101"]}'),
    ('organismo:vialidad', 'organismo', 'Dirección Nacional de Vialidad', '{"arce": ["10-3"]}'),
    ('organismo:hospital-de-clinicas', 'organismo', 'Hospital de Clínicas', '{"arce": ["26-15"]}'),
    ('organismo:hospital-maciel', 'organismo', 'Hospital Maciel (ASSE)', '{"arce": ["29-5"]}'),
    ('organismo:pereira-rossell', 'organismo', 'Centro Hospitalario Pereira Rossell (ASSE)', '{"arce": ["29-4"]}')
on conflict (id) do update set nombre = excluded.nombre, identificadores = excluded.identificadores;
