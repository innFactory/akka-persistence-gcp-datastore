#!/bin/bash

echo "Testing with datastore emulator"
echo "Starting database..."
gcloud beta emulators datastore start --no-store-on-disk --consistency=1.0 &
sleep 5
curl -X POST http://localhost:8081/reset
sbt test
trap 'kill $(jobs -pr)' SIGINT SIGTERM EXIT
