# Quick Start

Setup the OpenTSDB as datasource first.

    docker run -d --name opentsdb -p 60000:60000 -p 60010:60010 -p 60030:60030 -p 4242:4242 petergrace/opentsdb-docker:latest

Then use this Dockerfile:

    docker run -d --name metrilyx-dashboard -p 80:80 --link opentsdb:OPENTSDB dreampuf/metrilyx:latest

If you use the boot2docker on Mac like me, please with special WebSocket access address:

    docker run -d --name metrilyx-dashboard -e ACCESS_ADDRESS="192.168.59.103:8081" -p 8081:80 --link opentsdb:OPENTSDB dreampuf/metrilyx:latest

![Demo][1]

  [1]: http://i.imgur.com/P0UvoHv.png
