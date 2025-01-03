import Map from "ol/Map";
import VectorLayer from "ol/layer/Vector";
import VectorSource from "ol/source/Vector";
import Style from "ol/style/Style";
import Stroke from "ol/style/Stroke";
import {
  LocationOnly,
  makeControls,
  makeInteractions,
  makePrintoutView,
  makeStreetsLayer,
  MapRaster,
  rememberViewAdjustments,
  scaledTerritoryTextStyle,
  Territory,
  territoryStrokeStyle,
  wktToFeature,
  wktToFeatures
} from "./mapOptions.ts";
import {OpenLayersMapElement} from "./OpenLayersMap.ts";

export class RegionMapElement extends OpenLayersMapElement {
  createMap({root, mapRaster, settingsKey}) {
    const region = {
      location: this.getAttribute("region-location")
    };
    const territories = JSON.parse(this.getAttribute("territories") ?? "[]");
    const map = initRegionMap(root, region, territories, settingsKey);
    map.setStreetsLayerRaster(mapRaster);
    return map
  }
}

function initRegionMap(element: HTMLDivElement,
                       region: LocationOnly,
                       territories: Territory[],
                       settingsKey: string | null) {
  const regionLayer = new VectorLayer({
    source: new VectorSource({
      features: wktToFeatures(region.location)
    }),
    style: new Style({
      stroke: new Stroke({
        color: 'rgba(0, 0, 0, 0.6)',
        width: 4.0
      })
    })
  });

  const territoryLayer = new VectorLayer({
    source: new VectorSource({
      features: territories.map(function (territory) {
        const feature = wktToFeature(territory.location);
        feature.set('number', territory.number);
        return feature;
      })
    }),
    style: function (feature, resolution) {
      const number = feature.get('number');
      const style = new Style({
        stroke: territoryStrokeStyle(),
        text: scaledTerritoryTextStyle(number, feature, resolution)
      });
      return [style];
    }
  });

  const streetsLayer = makeStreetsLayer();

  function resetZoom(map, opts) {
    map.getView().fit(regionLayer.getSource()!.getExtent(), {
      padding: [5, 5, 5, 5],
      minResolution: 3.0,
      ...opts,
    });
  }

  const map = new Map({
    target: element,
    pixelRatio: 2, // render at high DPI for printing
    layers: [streetsLayer, regionLayer, territoryLayer],
    controls: makeControls({resetZoom}),
    interactions: makeInteractions(),
    view: makePrintoutView(),
  });
  resetZoom(map, {});
  rememberViewAdjustments(map, settingsKey);

  return {
    setStreetsLayerRaster(mapRaster: MapRaster) {
      streetsLayer.setSource(mapRaster.makeSource());
    },
    unmount() {
      map.setTarget(undefined)
    }
  };
}
