#!/bin/sh
echo "----------------------------------------------------------------"
echo "Starting Backend Application"
echo "DB_URL: $DB_URL"
echo "DB_USERNAME: $DB_USERNAME"
echo "SLICER_PATH: $SLICER_PATH"
echo "----------------------------------------------------------------"

# Exec java with explicit properties from env
exec java -jar app.jar \
  --spring.datasource.url="${DB_URL}" \
  --spring.datasource.username="${DB_USERNAME}" \
  --spring.datasource.password="${DB_PASSWORD}"
