// Copyright © 2015-2019 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import {enrichCongregation} from "./api";

const data = {
  "id": "e473cf8c-92de-4e03-803f-a33a7fbd509d",
  "name": "Demo Congregation",
  "territories": [{
    "id": "36075e2f-c703-40f7-96df-33cd275eaa07",
    "number": "101",
    "addresses": "Luotsikatu 10\nKatajanokankatu 3",
    "subregion": "Katajanokka",
    "meta": {},
    "location": "MULTIPOLYGON(((24.9655745822202 60.1679034309706,24.9656020817804 60.1674884704389,24.9662804042653 60.1675067105724,24.9662620712252 60.1679262308479,24.9655745822202 60.1679034309706)))"
  }, {
    "id": "eb771c05-139b-4023-ac65-5b9fb3ef30e1",
    "number": "102",
    "addresses": "Kauppiaankatu 7, 8-10",
    "subregion": "Katajanokka",
    "meta": {},
    "location": "MULTIPOLYGON(((24.9656204148205 60.16750215054,24.9656387478606 60.1671099853841,24.9663629029459 60.1671145454709,24.9663445699058 60.1675203906659,24.9656204148205 60.16750215054)),((24.9661033146067 60.167036053578,24.9661128107918 60.1666439511929,24.9667490551952 60.1666486753459,24.9667110704547 60.1670549499603,24.9661033146067 60.167036053578)))"
  }, {
    "id": "5be622a7-6588-455c-bb9a-f9ae0616d273",
    "number": "201",
    "addresses": "Itäinen Puistotie 11, 12\nOikotie 1",
    "subregion": "Kaivopuisto",
    "meta": {},
    "location": "MULTIPOLYGON(((24.958648131807 60.1571240474594,24.9590776329487 60.1572539613521,24.9597850465938 60.1572665336371,24.9601640181894 60.1570150870243,24.9590860545397 60.1567175393812,24.958648131807 60.1571240474594)),((24.9594397613623 60.1576772256348,24.9597850465938 60.1574048284542,24.9604419306929 60.1575934113581,24.9600461159152 60.1578616162911,24.9594397613623 60.1576772256348)))"
  }],
  "congregationBoundaries": [{
    "id": "43d061aa-d23e-45e8-8aab-52490e415723",
    "location": "MULTIPOLYGON(((24.9031539855307 60.1798620877479,24.893371963016 60.1706594757027,24.8992366681567 60.1503531884962,24.9119931625018 60.1437989047056,24.9654996471002 60.144620295441,24.988599362918 60.1676109083794,24.9708031532829 60.1761110157835,24.9539497891913 60.1772832717943,24.9505319740958 60.1762868568524,24.9446391894484 60.176990211714,24.9403963845023 60.1793346191399,24.9330893315395 60.1781038261079,24.9119931625018 60.1761110157835,24.9031539855307 60.1798620877479)))"
  }, {
    "id": "75a8a893-e1c8-4e0e-bb8a-3e42d4e9501a",
    "location": "MULTIPOLYGON(((24.9708434168563 60.148594222891,24.9760707434339 60.1518267449349,24.9868422042604 60.1518267449349,24.9971384535799 60.1479634498273,24.9958712228944 60.1429168297027,24.9901686848098 60.1360553384673,24.9803476469974 60.1373173293208,24.9700513976779 60.1453613830363,24.9708434168563 60.148594222891)))"
  }],
  "subregions": [{
    "id": "bb930579-6dee-4c7b-8225-318d736d9836",
    "name": "Katajanokka",
    "location": "MULTIPOLYGON(((24.9788725748902 60.1700084007474,24.9686917179976 60.1706852977515,24.959367665893 60.1693314897967,24.9558900464594 60.1668744365705,24.9696493233489 60.1622105444979,24.9833077996751 60.166648779613,24.9788725748902 60.1700084007474)))"
  }, {
    "id": "c6b72a21-72ca-47b5-b930-9892d785be63",
    "name": "Kaivopuisto",
    "location": "MULTIPOLYGON(((24.9564302912517 60.1612104979064,24.9550022667751 60.1589318234937,24.9502750133356 60.1555625261575,24.949954938884 60.1547538434062,24.9580799057332 60.1526462736672,24.9650723014457 60.1553542309874,24.9640135936442 60.1581109719637,24.9618469358178 60.1600344276142,24.9580306635098 60.160426455721,24.9564302912517 60.1612104979064)),((24.9629056436193 60.1615412600936,24.965934040354 60.1624600264805,24.9678052448405 60.1617740166742,24.9673128226072 60.1602059404863,24.9677067603938 60.1588705666246,24.9644813947658 60.1583560044175,24.9623886002744 60.1600466785633,24.9629056436193 60.1615412600936)))"
  }],
  "cardMinimapViewports": [{
    "id": "af5ea389-9d23-4fd3-af23-4e9d8892446e",
    "location": "POLYGON((24.9700513976779 60.1497768897728,24.9757539357625 60.1526151167376,24.9969800497442 60.1526151167376,24.9990392996081 60.1362130899715,24.9700513976779 60.1366863399454,24.9700513976779 60.1497768897728))"
  }, {
    "id": "cff27493-fd83-43f5-bc05-4f1004a0101e",
    "location": "POLYGON((24.8929087296998 60.1802749921541,24.9000369023056 60.1427591103448,24.9676753401426 60.1443362698894,24.9843077428895 60.1683785832478,24.9782883971335 60.1815352863533,24.8929087296998 60.1802749921541))"
  }]
};

export const demoCongregation = enrichCongregation(data);

export const territory101 = demoCongregation.getTerritoryById("36075e2f-c703-40f7-96df-33cd275eaa07");
export const territory102 = demoCongregation.getTerritoryById("eb771c05-139b-4023-ac65-5b9fb3ef30e1");
export const territory201 = demoCongregation.getTerritoryById("5be622a7-6588-455c-bb9a-f9ae0616d273");

export const subregionKaivopuisto = demoCongregation.getSubregionById("c6b72a21-72ca-47b5-b930-9892d785be63");
export const subregionKatajanokka = demoCongregation.getSubregionById("bb930579-6dee-4c7b-8225-318d736d9836");

export const viewportHelsinki = demoCongregation.cardMinimapViewports[1];
export const viewportSuomenlinna = demoCongregation.cardMinimapViewports[0];