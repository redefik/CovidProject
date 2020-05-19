#!/bin/bash

# This script create the directory used from Nifi to ingest files into HDFS and get files from it
hdfs dfs -mkdir /user/nifi
hdfs dfs -mkdir /user/nifi/covid_project
hdfs dfs -mkdir /user/nifi/covid_project/italian_data
hdfs dfs -mkdir /user/nifi/covid_project/global_data
hdfs dfs -chown -R nifi:nifi /user/nifi