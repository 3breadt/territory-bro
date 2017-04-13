import "./style.css";
import "whatwg-fetch";
import Auth0Lock from "auth0-lock";
import Lockr from "lockr";
import React from "react";
import ReactDOM from "react-dom";
import HelloForm from "./HelloForm";

var lock = new Auth0Lock('8tVkdfnw8ynZ6rXNndD6eZ6ErsHdIgPi', 'luontola.eu.auth0.com');

lock.on("authenticated", authResult => {
  console.log("authResult", authResult);
  loadUserProfile(authResult.idToken)
});

function loadUserProfile(idToken) {
  if (!idToken) {
    idToken = Lockr.get('id_token');
  }
  if (!idToken) {
    return;
  }
  lock.getProfile(idToken, (error, profile) => {
    if (error) {
      console.log("auth error", error);
      return;
    }
    console.log("profile", profile);
    Lockr.set('id_token', idToken);
    Lockr.set('profile', profile);
    showUserProfile(profile);
  });
}

function logout() {
  Lockr.rm('id_token');
  Lockr.rm('profile');
  window.location.href = "/";
}

function showUserProfile(profile) {
  var status = document.getElementById('status');
  status.textContent = "Logged in as " + profile.name + ", " + profile.email + " (" + profile.user_id + ")";
  var avatar = document.getElementById('avatar');
  avatar.src = profile.picture;
  avatar.style.display = 'block';
}

function init() {
  var btn_login = document.getElementById('btn-login');
  btn_login.addEventListener('click', () => lock.show());

  var btn_logout = document.getElementById('btn-logout');
  btn_logout.addEventListener('click', () => logout());

  loadUserProfile();
  loadTerritories();
}

function loadTerritories() {
  // TODO
  fetch('/api/territories', {
    method: 'get',
    headers: {
      'Accept': 'application/json',
      'Content-Type': 'application/json',
      'Authorization': 'Bearer ' + Lockr.get('id_token')
    }
  })
    .then(response => response.json())
    .then(json => console.log('parsed json', json))
    .catch(ex => console.log('parsing failed', ex))
}

init();

ReactDOM.render(
  <HelloForm/>,
  document.getElementById('root')
);