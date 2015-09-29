#/usr/bin/sh

if [ ! -z "$OPENTSDB_PORT_4242_TCP_PORT" ]; then
    sed -i "s/4242/$OPENTSDB_PORT_4242_TCP_PORT/" /opt/metrilyx/etc/metrilyx/metrilyx.conf
fi;

if [ ! -z "$ACCESS_ADDRESS" ]; then \
    export ACCESS_ADDRESS_HOST="${ACCESS_ADDRESS/:*/}"; \
    sed -i "/websocket/ a\ \t\t\"hostname\": \"${ACCESS_ADDRESS_HOST}\"," /opt/metrilyx/etc/metrilyx/metrilyx.conf; \
    export ACCESS_ADDRESS_PORT="${ACCESS_ADDRESS/*:/}"; \
    if [ ! -z "$ACCESS_ADDRESS_PORT" ]; then \
        sed -i "/websocket/ a\ \t\t\"port\": ${ACCESS_ADDRESS_PORT}," /opt/metrilyx/etc/metrilyx/metrilyx.conf; \
    fi; \
fi;

/etc/init.d/metrilyx start
/etc/init.d/nginx restart

tail -f /opt/metrilyx/log/*
