#!/bin/bash

docker rm my_nifi
docker rm my_postgres
docker rm my_pgadmin
docker rm my_grafana

docker network remove ingestion_serving_network
