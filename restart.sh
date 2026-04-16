#!/bin/bash
set -euo pipefail

COMPOSE_FILE="deploy/local/docker-compose.yml"

echo ">>> Stopping and removing all containers..."
docker compose -f "$COMPOSE_FILE" down --remove-orphans 2>/dev/null || true

echo ">>> Removing any conflicting containers..."
docker ps -aq | xargs -r docker rm -f 2>/dev/null || true

echo ">>> Pruning unused networks..."
docker network prune -f 2>/dev/null || true

echo ">>> Starting stack (no build)..."
docker compose -f "$COMPOSE_FILE" up -d

echo ""
echo ">>> Done. Services:"
echo "    Frontend      http://localhost:3000"
echo "    Ingestion     http://localhost:8081"
echo "    Incident      http://localhost:8082"
echo "    Realtime      http://localhost:8083"
echo "    Kafka UI      http://localhost:8090"
echo "    MongoDB       localhost:27017"
