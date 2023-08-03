// Copyright © 2015-2023 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import React from "react";
import styles from "./CropMarks.module.css";
import cropMarkUrl from "./crop-mark.svg";

type Props = {
  children?: React.ReactNode;
};

// TODO: parameterize the printout size?

const CropMarks = ({children}: Props) => {
  return <div className={styles.root}>
    <div className={styles.topLeft}><img src={cropMarkUrl} alt=""/></div>
    <div className={styles.topRight}><img src={cropMarkUrl} alt=""/></div>
    <div className={styles.cropArea}>{children}</div>
    <div className={styles.bottomLeft}><img src={cropMarkUrl} alt=""/></div>
    <div className={styles.bottomRight}><img src={cropMarkUrl} alt=""/></div>
  </div>;
};

export default CropMarks;
