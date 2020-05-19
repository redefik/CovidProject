#!/bin/bash
# This script is a setup of a Docker network encapsulating the ingestion and
# serving layer of the application. The network includes:
# - a NiFi instance used for importing file into HDFS and exporting query results
#   to a Postgres database
# - a Postgres instance for storing query results
# - a PgAdmin instance used to create a Postgres database storing the results
# - a Grafana instance used to graphically visualize query results

mkdir nifi_data
mkdir postgres_data
mkdir grafana_data

docker network create ingestion_serving_network

# Nifi setup
# NOTE: nifi_data directory must contain the configuration files of the Hadoop instance which Nifi will connect to and
#       the jar file of the JDBC driver used to interact with the Postgres database
# You can find the above jar file at https://jdbc.postgresql.org/download.html
docker run --name my_nifi --network=ingestion_serving_network \
          -p 8080:8080 -d  -v "$PWD/nifi_data:/data" \
           apache/nifi:latest

# Postgres setup
docker run --name my_postgres --network=ingestion_serving_network \
           -v "$PWD/postgres_data:/var/lib/postgresql/data" \
           -e 'POSTGRES_PASSWORD=pass' -d -p 5432:5432 postgres
       
# PgAdmin setup
docker run --name my_pgadmin --network=ingestion_serving_network \
           -p 80:80 -e 'PGADMIN_DEFAULT_EMAIL=user@domain.com' \
           -e 'PGADMIN_DEFAULT_PASSWORD=SuperSecret' \
           -d dpage/pgadmin4

# Grafana requires the permission to write in the grafana_data directory provided for persistence
ID=$(id -u)

# Grafana setup
docker run -d -p 3000:3000 --name my_grafana --network=ingestion_serving_network \
           --user $ID -v "$PWD/grafana_data:/var/lib/grafana" \
           -e 'GF_INSTALL_PLUGINS=natel-plotly-panel,grafana-worldmap-panel,michaeldmoore-multistat-panel' \
           -e 'GF_PLUGIN_DIR=/var/lib/grafana/plugins' grafana/grafana
       
       
