#!/bin/sh

echo "Remove any postgresql legacy"
echo "Re-generating the configuration files of LTU Engine 7.6..."
echo "Restarting LTU services..."
echo "Authentication to LTU Backend for allowing the bulk upload of markers..."

echo "Weki logs output for this instance"
tail -f /opt/ltuengine76/logs/weki.log
