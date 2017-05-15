// Copyright © 2015-2017 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

/* @flow */

import React from "react";
import styles from "./A5PrintFrame.css";

// TODO: parameterize the printout size?
// TODO: use @page CSS to remove print margins
// https://developer.mozilla.org/en/docs/Web/CSS/@page
// https://www.smashingmagazine.com/2015/01/designing-for-print-with-css/

const A5PrintFrame = ({children}: {
  children?: React.Element<*>,
}) => (
  <div className={styles.cropArea}>
    {children}
  </div>
);

export default A5PrintFrame;
