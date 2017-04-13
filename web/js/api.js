// Copyright © 2015-2017 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import {api} from "./util";
import alphanumSort from "alphanum-sort";
import _ from "lodash";

function sortTerritories(territories) {
  const numbers = alphanumSort(territories.map(t => t.number));
  return _.sortBy(territories, t => _.findIndex(numbers, n => n === t.number))
}

export async function getTerritories() {
  const response = await api.get('/api/territories');
  return sortTerritories(response.data);
}

function sortRegions(regions) {
  return _.sortBy(regions, r => r.name.toLowerCase());
}

export async function getRegions() {
  const response = await api.get('/api/regions');
  return sortRegions(response.data);
}