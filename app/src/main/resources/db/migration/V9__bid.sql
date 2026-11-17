-- El BID, una de las cinco fuentes de la versión 0 (ADR 0006, docs/fuentes/bid.md): las operaciones
-- para Uruguay aprobadas cada noche, de la tabla de proyectos de su portal de datos abiertos (CC BY 4.0).
-- El robots.txt del portal se volvió a leer el 2026-11-17: la API está abierta.
insert into parte.fuentes (id, nombre, url, licencia, verificada_en) values
    ('bid', 'Banco Interamericano de Desarrollo (BID)', 'https://data.iadb.org/dataset/idb-projects-dataset',
     'Creative Commons Atribución 4.0 (CC BY 4.0)', date '2026-11-17');

-- Los textos del BID están en inglés: el BPS aparece por su sigla o por su nombre en inglés.
update parte.entidades
set identificadores = identificadores || '{"bid": ["Banco de Previsión Social", "Social Security Bank", "BPS"]}'::jsonb
where id = 'organismo:bps';
