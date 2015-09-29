#!/bin/bash
sudo curl -L https://github.com/docker/compose/releases/download/1.4.2/docker-compose-`uname -s`-`uname -m` > /opt/bin/docker-compose
sudo chmod +x /opt/bin/docker-compose
sudo chown core:core /opt/bin/docker-compose
docker-compose version

