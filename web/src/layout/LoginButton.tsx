// Copyright © 2015-2023 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import {auth0Authenticator} from "../authentication";
import {useSettings} from "../api";

const LoginButton = () => {
  const settings = useSettings();
  return <button type="button" className="pure-button" onClick={() => {
    auth0Authenticator(settings).login();
  }}>Login</button>;
};

export default LoginButton;
