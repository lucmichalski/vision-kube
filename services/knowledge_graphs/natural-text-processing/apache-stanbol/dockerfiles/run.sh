#!/bin/bash

# We can not access environment variables in
# supervisor conf. files easily, that's why
# we need to start the Stanbol this way.
exec java ${JAVA_OPTS} -jar /opt/stanbol-launcher.jar
