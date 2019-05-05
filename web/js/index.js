// Copyright © 2015-2019 Esko Luontola
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

import React from "react";
import ReactDOM from "react-dom";
import {globalHistory, Router} from "@reach/router"
import {IntlProvider} from "react-intl";
import {language, messages} from "./intl";
import ErrorBoundary from "react-error-boundary";
import OverviewPage from "./pages/OverviewPage";
import CongregationPage from "./pages/CongregationPage";
import RegistrationPage from "./pages/RegistrationPage";
import LoginCallbackPage from "./pages/LoginCallbackPage";
import NotFoundPage from "./pages/NotFoundPage";
import Layout from "./layout/Layout";
import PrintoutPage from "./pages/PrintoutPage";

const root = ReactDOM.unstable_createRoot(document.getElementById('root'));

globalHistory.listen(({location, action}) => {
  console.info(`Current URL is now ${location.pathname}${location.search}${location.hash} (${action})`);
});

const ErrorPage = ({componentStack, error}) => (
  <>
    <h1>Sorry, something went wrong 🥺</h1>
    <pre>{`${error.stack}\n\nThe error is located at:${componentStack}`}</pre>
  </>
);

root.render(
  <React.StrictMode>
    <ErrorBoundary FallbackComponent={ErrorPage}>
      <IntlProvider locale={language} messages={messages}>
        <React.Suspense fallback={<p>Loading....</p>}>
          <Layout>
            <Router>
              <OverviewPage path="/"/>
              <RegistrationPage path="/register"/>
              <LoginCallbackPage path="/login-callback"/>
              <CongregationPage path="/congregation/:congregationId"/>
              <PrintoutPage path="/congregation/:congregationId/printouts"/>
              <NotFoundPage default/>
            </Router>
          </Layout>
        </React.Suspense>
      </IntlProvider>
    </ErrorBoundary>
  </React.StrictMode>
);
