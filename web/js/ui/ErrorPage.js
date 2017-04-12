// Copyright © 2015-2017 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import React from "react";
import {Layout} from "./Layout";

let ErrorPage = ({error}) => (
  <Layout>
    <h1>Error {error.status}: {error.message}</h1>
  </Layout>
);

export {ErrorPage};
