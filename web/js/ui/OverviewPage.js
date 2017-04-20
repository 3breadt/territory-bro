// Copyright © 2015-2017 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

/* @flow */

import React from "react";
import {Layout} from "./Layout";

let OverviewPage = ({territoryCount, regionCount}: { territoryCount: number, regionCount: number }) => (
  <Layout>
    <h1>Territory Bro</h1>

    <p>Territory Bro is a tool for managing territory cards in the congregations of Jehovah's Witnesses.</p>

    <p>For more information, see <a href="http://territorybro.com">http://territorybro.com</a></p>

    <h2>System Requirements</h2>

    <p>The territory card UI uses <a href="http://caniuse.com/#feat=css-variables">CSS varibles</a> and <a
      href="http://caniuse.com/#feat=css-grid">Grid layout</a>, which are only supported by Chrome 48 when you enable
      "Experimental Web Platform features" in chrome://flags</p>

    <h2>Import</h2>

    <p>The database has currently {territoryCount} territories and {regionCount} regions.</p>

    <form action="/clear-database" method="post">
      <button type="submit" className="btn btn-primary">Delete All</button>
    </form>

    <form action="/import-territories" method="post" encType="multipart/form-data">
      <p>Territories GeoJSON: <input type="file" name="territories"/></p>
      <p>Regions GeoJSON: <input type="file" name="regions"/></p>
      <button type="submit" className="btn btn-primary">Import</button>
    </form>
  </Layout>
);

export {OverviewPage};
