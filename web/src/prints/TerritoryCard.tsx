// Copyright © 2015-2023 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import {FormattedMessage} from "react-intl";
import TerritoryMap from "../maps/TerritoryMap";
import TerritoryMiniMap from "../maps/TerritoryMiniMap";
import {useCongregationById} from "../api";
import CropMarks from "./CropMarks";
import styles from "./TerritoryCard.module.css";
import PrintDateNotice from "./PrintDateNotice";
import TerritoryQrCode from "./TerritoryQrCode";
import {findMapRasterById} from "../maps/mapOptions.ts";
import {memo} from "react";

const TerritoryCard = ({
                         territory,
                         territoryId,
                         congregation,
                         congregationId,
                         qrCodeUrl,
                         mapRaster,
                         mapRasterId
                       }) => {
  congregation = congregation || useCongregationById(congregationId);
  territory = territory || congregation.getTerritoryById(territoryId);
  mapRaster = mapRaster || findMapRasterById(mapRasterId);
  return <CropMarks>
    <div className={styles.root}>

      <div className={styles.minimap}>
        <TerritoryMiniMap territory={territory} congregation={congregation} printout={true}/>
      </div>

      <div className={styles.header}>
        <div className={styles.title}>
          <FormattedMessage id="TerritoryCard.title" defaultMessage="Territory Map Card"/>
        </div>
        <div className={styles.region}>
          {territory.region}
        </div>
      </div>

      <div className={styles.number}>
        {territory.number}
      </div>

      <div className={styles.map}>
        <PrintDateNotice>
          <TerritoryMap territory={territory} mapRaster={mapRaster} printout={true}/>
        </PrintDateNotice>
      </div>

      <div className={styles.addresses}>
        {qrCodeUrl &&
          <div className={styles.qrCode}>
            <TerritoryQrCode value={qrCodeUrl}/>
          </div>
        }
        {territory.addresses}
      </div>

      <div className={styles.footer}>
        <FormattedMessage id="TerritoryCard.footer"
                          defaultMessage={"Please keep this card in the envelope. Do not soil, mark or bend it. \n Each time the territory is covered, please inform the brother who cares for the territory files."}/>
      </div>

    </div>
  </CropMarks>;
};

export default memo(TerritoryCard);
