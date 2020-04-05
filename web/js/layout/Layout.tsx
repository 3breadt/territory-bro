// Copyright © 2015-2019 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import "./defaultStyles";
import * as React from "react";
import AuthenticationPanel from "./AuthenticationPanel";
import {Link} from "@reach/router";

type Props = {
  title?: string;
  children?: React.ReactNode;
};

class Layout extends React.Component<Props> {

  componentDidMount() {
    // TODO: get title from H1
    const {
      title
    } = this.props;
    const site = "Territory Bro";
    document.title = title ? `${title} - ${site}` : site;
  }

  render() {
    const {
      children
    } = this.props;
    return <div id="layout">

      <nav className="no-print">
        <AuthenticationPanel/>
        <ul>
          <li><Link to="/">Home</Link></li>
          <li><a href="https://territorybro.com/guide/">User Guide</a></li>
          <li><a href="https://groups.google.com/forum/#!forum/territory-bro-announcements">Updates</a></li>
          <li><Link to="/support">Support</Link></li>
        </ul>
      </nav>

      <main className="container">
        {children}
      </main>

    </div>;
  }
}

export default Layout;
