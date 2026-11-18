-- El Banco Mundial, una de las fuentes de la versión 0, como fuente secundaria (ADR 0006,
-- docs/fuentes/banco-mundial.md): los proyectos para Uruguay que el directorio aprueba cada noche, de la
-- versión 3 de su API de búsqueda (datos con licencia CC BY 4.0). La API se volvió a probar el 2026-11-18.
insert into parte.fuentes (id, nombre, url, licencia, verificada_en) values
    ('banco-mundial', 'Banco Mundial', 'https://projects.worldbank.org/en/projects-operations/projects-list?countrycode_exact=UY',
     'Creative Commons Atribución 4.0 (CC BY 4.0)', date '2026-11-18');

-- Los textos del Banco Mundial están en inglés: el BPS aparece por su sigla, por su nombre o por su nombre
-- en inglés.
update parte.entidades
set identificadores = identificadores
    || '{"banco-mundial": ["Banco de Previsión Social", "Social Security Bank", "Social Security Institute", "BPS"]}'::jsonb
where id = 'organismo:bps';
