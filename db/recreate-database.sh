#!/bin/bash
set -eux

PROJECT=territorybro

psql <<EOF
DROP DATABASE IF EXISTS $PROJECT;
DROP USER IF EXISTS $PROJECT;
CREATE USER $PROJECT WITH PASSWORD '$PROJECT';
CREATE DATABASE $PROJECT
    OWNER $PROJECT
    ENCODING 'UTF-8'
    LC_COLLATE 'fi_FI.UTF-8'
    LC_CTYPE 'fi_FI.UTF-8'
    TEMPLATE template0;
\connect $PROJECT;
CREATE SCHEMA $PROJECT AUTHORIZATION $PROJECT;
CREATE EXTENSION postgis;
EOF
