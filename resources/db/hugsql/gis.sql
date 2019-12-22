-- :name get-congregation-boundaries :? :*
select id, ST_AsText(location) AS location
from congregation_boundary;

-- :name create-congregation-boundary :!
insert into congregation_boundary (id, location)
values (:id, ST_Multi(ST_GeomFromText(:location)));


-- :name get-subregions :? :*
select id, name, ST_AsText(location) AS location
from subregion;

-- :name create-subregion :!
insert into subregion (id, name, location)
values (:id, :name, ST_Multi(ST_GeomFromText(:location)));


-- :name get-card-minimap-viewports :? :*
select id, ST_AsText(location) AS location
from card_minimap_viewport;

-- :name create-card-minimap-viewport :!
insert into card_minimap_viewport (id, location)
values (:id, ST_GeomFromText(:location));


-- :name get-territories :? :*
select id, number, addresses, subregion, meta, ST_AsText(location) AS location
from territory
where 1 = 1
/*~ (when (contains? params :ids) */
  and id = any(array[:v*:ids]::uuid[])
/*~ ) ~*/
;

-- :name create-territory :!
insert into territory (id, number, addresses, subregion, meta, location)
values (:id, :number, :addresses, :subregion, :meta, ST_Multi(ST_GeomFromText(:location)));


-- :name get-gis-changes :? :*
select id, schema, "table", op, "user", time, old, new
from gis_change_log
where 1 = 1
/*~ (when (contains? params :since) */
  and id > :since
/*~ ) ~*/
order by id;
