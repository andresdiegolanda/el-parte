-- La normativa de Presidencia, una de las fuentes de la versión 0, leída a ritmo bajo y citando siempre la
-- fuente (ADR 0006, docs/fuentes/presidencia.md): las resoluciones de cada noche, del listado de gub.uy.
-- Las leyes y los decretos llegan por IMPO. El robots.txt de gub.uy se volvió a leer el 2026-11-18: no
-- prohíbe la normativa ni sus filtros. Los términos de uso no dan una licencia explícita de reutilización.
insert into parte.fuentes (id, nombre, url, licencia, verificada_en) values
    ('presidencia', 'Presidencia de la República: normativa', 'https://www.gub.uy/presidencia/institucional/normativa',
     'Sin licencia explícita: los términos de uso de gub.uy admiten el acceso por aplicaciones, a ritmo moderado y citando la fuente',
     date '2026-11-18');

-- En las resoluciones el BPS aparece por su nombre o por su sigla.
update parte.entidades
set identificadores = identificadores || '{"presidencia": ["Banco de Previsión Social", "BPS"]}'::jsonb
where id = 'organismo:bps';
