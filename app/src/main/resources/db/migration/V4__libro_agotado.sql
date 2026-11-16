-- El presupuesto que corta (ADR 0005, T22). El primer límite que no alcanza queda en el libro de la
-- corrida, y desde ahí se rechazan todas las llamadas siguientes de esa corrida, aunque alguna cupiera.
alter table parte.libros_de_corrida
    add column agotado text check (agotado in ('costo', 'pasos', 'tiempo'));
