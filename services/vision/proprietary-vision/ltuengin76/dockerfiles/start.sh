#!/bin/sh
HOSTY=$(`hostname -f`)
apt-get update
sed -i -e "s/0214bf566d99/$HOSTY/g" /opt/ltu-engine-7.6.3/licence.lic
cd /opt/ltu-engine-7.6.3 && ls -l && ./install.sh /opt/ltuengine76; echo ok
service ltud restart all
