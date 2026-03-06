#!/usr/bin/env bash

set -u

BACKEND_URL="${BACKEND_URL:-http://localhost:8080}"
HEALTH_URL="${HEALTH_URL:-${BACKEND_URL}/api/health}"
VERSION_URL="${VERSION_URL:-${BACKEND_URL}/api/version}"

MAX_RETRIES="${MAX_RETRIES:-30}"
SLEEP_SECONDS="${SLEEP_SECONDS:-2}"
DB_CONTAINER="${DB_CONTAINER:-}"
DB_USER="${DB_USER:-${POSTGRES_USER:-postgres}}"

PASS_COUNT=0
FAIL_COUNT=0

pass() {
  echo "[PASS] $1"
  PASS_COUNT=$((PASS_COUNT + 1))
}

fail() {
  echo "[FAIL] $1"
  FAIL_COUNT=$((FAIL_COUNT + 1))
}

info() {
  echo "[INFO] $1"
}

wait_for_backend() {
  info "Attendo backend su ${HEALTH_URL} (max ${MAX_RETRIES} tentativi)..."
  local attempt=1
  while [ "$attempt" -le "$MAX_RETRIES" ]; do
    if curl -sSf --max-time 5 "$HEALTH_URL" >/dev/null 2>&1; then
      pass "Backend raggiungibile"
      return 0
    fi
    sleep "$SLEEP_SECONDS"
    attempt=$((attempt + 1))
  done
  fail "Backend non raggiungibile entro timeout"
  return 1
}

resolve_db_container() {
  if [ -n "$DB_CONTAINER" ]; then
    echo "$DB_CONTAINER"
    return 0
  fi

  DB_CONTAINER="$(docker ps --filter "ancestor=postgres" --format '{{.Names}}' | head -n 1)"
  if [ -n "$DB_CONTAINER" ]; then
    echo "$DB_CONTAINER"
    return 0
  fi

  return 1
}

wait_for_db() {
  info "Attendo servizio DB PostgreSQL (max ${MAX_RETRIES} tentativi)..."

  local container
  if ! container="$(resolve_db_container)"; then
    fail "Container PostgreSQL non trovato (imposta DB_CONTAINER oppure avvia compose)"
    return 1
  fi

  local attempt=1
  while [ "$attempt" -le "$MAX_RETRIES" ]; do
    if docker exec "$container" pg_isready -U "$DB_USER" >/dev/null 2>&1; then
      pass "DB raggiungibile via pg_isready nel container '$container'"
      return 0
    fi
    sleep "$SLEEP_SECONDS"
    attempt=$((attempt + 1))
  done

  fail "DB non raggiungibile entro timeout nel container '$container'"
  return 1
}

check_health_payload() {
  info "Verifico payload ${HEALTH_URL}"
  local body
  if ! body="$(curl -sSf --max-time 8 "$HEALTH_URL" 2>/dev/null)"; then
    fail "Chiamata ${HEALTH_URL} fallita"
    return 1
  fi

  if echo "$body" | grep -Eq '"database"[[:space:]]*:[[:space:]]*"UP"' && \
     echo "$body" | grep -Eq '"status"[[:space:]]*:[[:space:]]*"UP"'; then
    pass "Health check OK (database=UP, status=UP)"
    return 0
  fi

  fail "Health check KO: attesi database=UP e status=UP"
  return 1
}

check_version_payload() {
  info "Verifico payload ${VERSION_URL}"
  local body
  if ! body="$(curl -sSf --max-time 8 "$VERSION_URL" 2>/dev/null)"; then
    fail "Chiamata ${VERSION_URL} fallita"
    return 1
  fi

  if echo "$body" | grep -Eq '"application"[[:space:]]*:' && \
     echo "$body" | grep -Eq '"version"[[:space:]]*:'; then
    pass "Version endpoint OK (chiavi application e version presenti)"
    return 0
  fi

  fail "Version endpoint KO: chiavi application/version mancanti"
  return 1
}

main() {
  echo "=== Sprint 0 QA Smoke Test ==="
  echo "Backend: ${BACKEND_URL}"
  echo "Health:  ${HEALTH_URL}"
  echo "Version: ${VERSION_URL}"
  echo "Retries: ${MAX_RETRIES}, Sleep: ${SLEEP_SECONDS}s"
  echo

  wait_for_db
  wait_for_backend
  check_health_payload
  check_version_payload

  echo
  echo "=== Riepilogo ==="
  echo "PASS: ${PASS_COUNT}"
  echo "FAIL: ${FAIL_COUNT}"

  if [ "$FAIL_COUNT" -gt 0 ]; then
    exit 1
  fi

  exit 0
}

main