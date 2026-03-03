#!/bin/sh
echo "----------------------------------------------------------------"
echo "Starting Backend Application"
echo "DB_URL: $DB_URL"
echo "DB_USERNAME: $DB_USERNAME"
echo "SPRING_DATASOURCE_URL: $SPRING_DATASOURCE_URL"
echo "SLICER_PATH: $SLICER_PATH"
echo "----------------------------------------------------------------"

# Determine which environment variables to use for database connection
# This allows compatibility with different docker-compose configurations
FINAL_DB_URL="${DB_URL:-$SPRING_DATASOURCE_URL}"
FINAL_DB_USER="${DB_USERNAME:-$SPRING_DATASOURCE_USERNAME}"
FINAL_DB_PASS="${DB_PASSWORD:-$SPRING_DATASOURCE_PASSWORD}"

if [ -n "$FINAL_DB_URL" ]; then
  echo "Using database URL: $FINAL_DB_URL"
  exec java -jar app.jar \
    --spring.datasource.url="${FINAL_DB_URL}" \
    --spring.datasource.username="${FINAL_DB_USER}" \
    --spring.datasource.password="${FINAL_DB_PASS}"
else
  echo "No database URL specified in environment, relying on application.properties defaults."
  exec java -jar app.jar
fi
