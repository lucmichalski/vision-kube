#!/bin/bash
sh /root/spark/bin/spark-submit \
--driver-memory 25G \
--class org.deeplearning4j.App \
--executor-memory 25G \
--master spark://ec2-54-148-164-15.us-west-2.compute.amazonaws.com:7077 \
target/scene-classification-spark-1.0-SNAPSHOT.jar
