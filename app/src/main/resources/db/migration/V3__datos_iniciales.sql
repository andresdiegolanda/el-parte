-- Datos iniciales de la versión 0. Nada de esto es un dato personal.

-- La fuente de la primera rebanada (ADR 0006, docs/fuentes/compras-estatales.md).
insert into parte.fuentes (id, nombre, url, licencia, verificada_en) values
    ('arce', 'Compras estatales (ARCE)', 'https://www.comprasestatales.gub.uy/ocds/',
     'Licencia de Datos Abiertos de Uruguay (Decreto 54/017)', date '2026-11-07');

-- El organismo del banco de pruebas. En ARCE compra con el código de inciso y unidad
-- ejecutora 28-1 (docs/fuentes/compras-estatales.md).
insert into parte.entidades (id, tipo, nombre, identificadores) values
    ('organismo:bps', 'organismo', 'Banco de Previsión Social', '{"arce": ["28-1"]}');

-- La cuenta de demostración: sin correo (ADR 0008), suscripta al organismo del banco de pruebas.
insert into parte.clientes (cliente_id, alias, es_demo) values
    ('00000000-0000-0000-0000-00000000de00', 'Demostración', true);
insert into parte.suscripciones (cliente_id, entidad_id) values
    ('00000000-0000-0000-0000-00000000de00', 'organismo:bps');

-- Modelos iniciales de cada agente (ADR 0003). Se cambian con una fila nueva (T29).
insert into parte.modelos_de_agente (agente, modelo, desde) values
    ('explorador-arce', 'claude-haiku-5-5',  timestamptz '2026-11-14 00:00:00+00'),
    ('archivista',      'claude-haiku-5-5',  timestamptz '2026-11-14 00:00:00+00'),
    ('refutador',       'claude-sonnet-5-5', timestamptz '2026-11-14 00:00:00+00'),
    ('analista',        'claude-sonnet-5-5', timestamptz '2026-11-14 00:00:00+00'),
    ('editor',          'claude-haiku-5-5',  timestamptz '2026-11-14 00:00:00+00');

-- Precios por millón de tokens (ADR 0005). Entrada y salida: PROVEN en
-- https://docs.claude.com/en/docs/about-claude/pricing.md el 2026-11-07.
-- Caché: ASSUMED (lectura a la décima parte de la entrada, escritura a 1,25 veces);
-- la primera rebanada no usa caché, y la tabla se verifica con el presupuesto (T22).
insert into parte.precios_de_modelo (modelo, usd_millon_entrada, usd_millon_salida, usd_millon_cache_lectura,
                                     usd_millon_cache_escritura, fuente_url, consultado_el, desde) values
    ('claude-haiku-5-5',  0.10,  0.50, 0.01,  0.125, 'https://docs.claude.com/en/docs/about-claude/pricing.md',
     date '2026-11-07', timestamptz '2026-11-14 00:00:00+00'),
    ('claude-sonnet-5-5', 2.00, 10.00, 0.20,  2.50,  'https://docs.claude.com/en/docs/about-claude/pricing.md',
     date '2026-11-07', timestamptz '2026-11-14 00:00:00+00');
