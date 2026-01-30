#!/bin/bash
set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" <<-EOSQL
    CREATE DATABASE swp_backend;
    CREATE DATABASE swp_ml;
EOSQL
