#!/usr/bin/env bash
set -euo pipefail

PLANNING_ROLE_PASSWORD=$(cat /run/secrets/planning_db_password)
: "${PLANNING_ROLE_PASSWORD:?Missing runtime role password}"
export PLANNING_ROLE_PASSWORD
psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" --single-transaction --file=- <<'SQL'
\getenv runtime_password PLANNING_ROLE_PASSWORD
CREATE ROLE curricula_planner LOGIN PASSWORD :'runtime_password';
REVOKE CONNECT ON DATABASE curricula_planning FROM PUBLIC;
GRANT CONNECT ON DATABASE curricula_planning TO curricula_planner;
\i /opt/curricula/001-planning.sql
GRANT USAGE ON SCHEMA curricula_planning TO curricula_planner;
GRANT SELECT, INSERT ON curricula_planning.account TO curricula_planner;
GRANT SELECT, INSERT, UPDATE ON curricula_planning.lesson_plan TO curricula_planner;
SQL
