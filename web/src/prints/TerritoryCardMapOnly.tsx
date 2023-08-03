// Copyright © 2015-2023 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import {FormattedMessage} from "react-intl";
import TerritoryMap from "../maps/TerritoryMap";
import TerritoryMiniMap from "../maps/TerritoryMiniMap";
import {getCongregationById} from "../api";
import CropMarks from "./CropMarks";
import styles from "./TerritoryCardMapOnly.module.css";
import PrintDateNotice from "./PrintDateNotice";
import TerritoryQrCode from "./TerritoryQrCode";

// TODO: deduplicate with TerritoryCard

const TerritoryCardMapOnly = ({
                                territory,
                                territoryId,
                                congregation,
                                congregationId,
                                qrCodeUrl,
                                mapRaster
                              }) => {
  congregation = congregation || getCongregationById(congregationId);
  territory = territory || congregation.getTerritoryById(territoryId);
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

      {qrCodeUrl &&
        <div className={styles.qrCode}>
          <TerritoryQrCode value={qrCodeUrl}/>
        </div>
      }

      <div className={styles.footer}>
        <FormattedMessage id="TerritoryCard.footer"/>
      </div>

    </div>
  </CropMarks>;
};

export default TerritoryCardMapOnly;