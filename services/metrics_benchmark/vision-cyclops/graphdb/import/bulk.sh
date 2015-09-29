#!/bin/bash
bin/neo4j-import --into retail.db --id-type string \
                 --nodes:Customer customers.csv --nodes products.csv  \
                 --nodes orders_header.csv,orders1.csv,orders2.csv \
                 --relationships:CONTAINS order_details.csv \
                 --relationships:ORDERED customer_orders_header.csv,orders1.csv,orders2.csv

