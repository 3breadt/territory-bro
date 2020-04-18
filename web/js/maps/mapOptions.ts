// Copyright © 2015-2020 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import Feature from "ol/Feature";
import {defaults as controlDefaults} from "ol/control";
import Attribution from "ol/control/Attribution";
import Source from "ol/source/Source";
import OSM, {ATTRIBUTION as OSM_ATTRIBUTION} from "ol/source/OSM";
import XYZ from "ol/source/XYZ";
import WKT from "ol/format/WKT";
import Tile from "ol/layer/Tile";
import Stroke from "ol/style/Stroke";
import Fill from "ol/style/Fill";
import Text from "ol/style/Text";
import TileWMS from "ol/source/TileWMS";
import {defaults as interactionDefaults} from "ol/interaction";
import DragPan from "ol/interaction/DragPan"
import MouseWheelZoom from "ol/interaction/MouseWheelZoom"
import {platformModifierKeyOnly} from "ol/events/condition";

export type MapRaster = {
  id: string;
  name: string;
  source: Source;
};
export const mapRasters: Array<MapRaster> = [{
  id: 'osm',
  name: "World - OpenStreetMap",
  source: new XYZ({
    url: '//a.osm.rrze.fau.de/osmhd/{z}/{x}/{y}.png',
    tileSize: [256, 256],
    tilePixelRatio: 2,
    attributions: OSM_ATTRIBUTION
  })
}, {
  id: 'osmBackup',
  name: "World - OpenStreetMap (backup server, low DPI)",
  source: new OSM()
}, {
  id: 'mmlTaustakartta',
  name: "Finland - Maanmittauslaitoksen taustakarttasarja",
  source: new XYZ({
    url: '//tiles.kartat.kapsi.fi/taustakartta/{z}/{x}/{y}.jpg',
    tileSize: [128, 128],
    tilePixelRatio: 2,
    attributions: '&copy; Maanmittauslaitos'
  })
}, {
  id: 'vantaaKaupunkikartta',
  name: "Finland - Vantaan kaupunkikartta",
  source: new TileWMS({
    url: 'https://gis.vantaa.fi/geoserver/wms',
    params: {'LAYERS': 'taustakartta:kaupunkikartta_single_layer', 'TILED': true},
    serverType: 'geoserver',
    attributions: '&copy; Vantaan kaupunki'
  })
}];

export function wktToFeature(wkt: string): Feature {
  const feature = new WKT().readFeature(wkt);
  feature.getGeometry().transform('EPSG:4326', 'EPSG:3857');
  return feature;
}

export function wktToFeatures(wkt: string | null | undefined): Array<Feature> {
  if (wkt) {
    return [wktToFeature(wkt)];
  } else {
    return [];
  }
}

export function makeStreetsLayer() {
  return new Tile({
    source: mapRasters[0].source
  });
}

export function makeControls() {
  return controlDefaults({attribution: false}).extend([
    new Attribution({
      className: 'map-attribution',
      collapsible: false
    })
  ]);
}

export function makeInteractions() {
  return interactionDefaults({dragPan: false, mouseWheelZoom: false}).extend([
    new DragPan({
      condition: function (event) {
        return this.getPointerCount() === 2 || platformModifierKeyOnly(event);
      }
    }),
    new MouseWheelZoom({
      condition: platformModifierKeyOnly
    })
  ])
}

// visual style

export function territoryStrokeStyle() {
  return new Stroke({
    color: 'rgba(255, 0, 0, 0.6)',
    width: 2.0
  });
}

export function territoryFillStyle() {
  return new Fill({
    color: 'rgba(255, 0, 0, 0.1)'
  });
}

export function territoryTextStyle(territoryNumber: string, fontSize: string) {
  return new Text({
    text: territoryNumber,
    font: 'bold ' + fontSize + ' sans-serif',
    fill: new Fill({color: 'rgba(0, 0, 0, 1.0)'}),
    stroke: new Stroke({color: 'rgba(255, 255, 255, 1.0)', width: 3.0}),
    overflow: true
  });
}
