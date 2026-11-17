-- IMPO, la quinta fuente de la versión 0 (ADR 0010, docs/fuentes/impo.md): solo el JSON de las leyes y
-- los decretos del Diario Oficial, sin avisos y con las personas tapadas. Las condiciones de uso y el
-- robots.txt se volvieron a leer el 2026-11-17.
insert into parte.fuentes (id, nombre, url, licencia, verificada_en) values
    ('impo', 'Diario Oficial (IMPO)', 'https://www.impo.com.uy/diariooficial',
     'Licencia de Datos Abiertos de Uruguay, la que IMPO ofrece para el JSON de las normas (ADR 0010)', date '2026-11-17');

-- Cómo se nombra al BPS en las normas: por su nombre y por su sigla. Las demás entidades del catálogo
-- llevan la sigla entre paréntesis en su nombre, y el explorador la saca de ahí.
update parte.entidades
set identificadores = identificadores || '{"impo": ["Banco de Previsión Social", "BPS"]}'::jsonb
where id = 'organismo:bps';
