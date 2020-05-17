#!/bin/bash

# This script installs git and docker on an EC2 instance (Linux AMI)
sudo yum update -y
sudo yum install git -y
sudo yum install docker -y
sudo service docker start
# Make docker usable without sudoing
sudo usermod -a -G docker ec2-user
echo "Please, exit and re-login to make changes effective"

