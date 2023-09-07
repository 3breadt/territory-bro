// Copyright © 2015-2023 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import PrintOptionsForm from "../prints/PrintOptionsForm";
import MapInteractionHelp from "../maps/MapInteractionHelp";
import {useParams} from "react-router-dom";
import {useTranslation} from "react-i18next";

const PrintoutPage = () => {
  const {t} = useTranslation();
  const {congregationId} = useParams()
  return <>
    <div className="no-print">
      <h1>{t('PrintoutPage.title')}</h1>
    </div>
    <PrintOptionsForm congregationId={congregationId}/>
    <div className="no-print">
      <MapInteractionHelp/>
    </div>
  </>;
};

export default PrintoutPage;
