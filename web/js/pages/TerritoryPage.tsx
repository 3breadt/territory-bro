// Copyright © 2015-2020 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import React from "react";
import {getCongregationById} from "../api";
import styles from "./TerritoryPage.css"
import TerritoryMap from "../maps/TerritoryMap";
import {mapRasters} from "../maps/mapOptions";
import MapInteractionHelp from "../maps/MapInteractionHelp";

const mapRaster = mapRasters[0];

const TerritoryPage = ({congregationId, territoryId}) => {
  const congregation = getCongregationById(congregationId);
  const territory = congregation.getTerritoryById(territoryId);
  // TODO: consider using a grid layout for responsiveness so that the details area has fixed width
  return <>
    <h1>Territory {territory.number}</h1>

    <div className="pure-g">
      <div className="pure-u-1 pure-u-sm-2-3 pure-u-md-1-2 pure-u-lg-1-3 pure-u-xl-1-4">
        <div className={styles.details}>
          <table className="pure-table pure-table-horizontal">
            <tbody>
            <tr>
              <th>Number</th>
              <td>{territory.number}</td>
            </tr>
            <tr>
              <th>Region</th>
              <td>{territory.region}</td>
            </tr>
            <tr>
              <th>Addresses</th>
              <td>{territory.addresses}</td>
            </tr>
            </tbody>
          </table>
        </div>
      </div>

      <div className="pure-u-1 pure-u-lg-2-3 pure-u-xl-3-4">
        <div className={styles.map}>
          <TerritoryMap territory={territory} mapRaster={mapRaster} printout={false}/>
        </div>
        <MapInteractionHelp/>
      </div>
    </div>
  </>;
};

export default TerritoryPage;