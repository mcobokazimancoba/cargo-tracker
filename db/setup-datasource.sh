#!/usr/bin/env bash
#
# Recreates the GlassFish-side JDBC plumbing the app expects:
#   - copies the PostgreSQL driver into GlassFish's domain lib
#   - drops any prior pool/resource by the same names (idempotent)
#   - creates the connection pool      (cargoTrackerPool)
#   - creates the JNDI JDBC resource   (jdbc/cargoTrackerDS)
#   - pings the pool to verify the DB is reachable
#
# This is what `persistence.xml` looks up via
#   <jta-data-source>jdbc/cargoTrackerDS</jta-data-source>
#
# WHY a script and not just admin-console clicks:
#   - Reproducible — committed to the repo, so a teammate (or future-you)
#     can rebuild the pool after a domain reset in one command.
#   - Idempotent — safe to run twice. The delete-then-create lets you fix
#     a bad password by editing the env var below and re-running.
#
# USAGE
# -----
#   1. Fill in DB_PASSWORD below (or export it before running).
#   2. Make sure GlassFish is running:        asadmin start-domain
#   3. Make sure PostgreSQL is running and the database exists.
#   4. Run:                                   bash db/setup-datasource.sh
#
# Pass GLASSFISH_HOME if `asadmin` is not on your PATH:
#   GLASSFISH_HOME=/opt/glassfish7 bash db/setup-datasource.sh
# ---------------------------------------------------------------------------

set -euo pipefail

# ── Configuration ──────────────────────────────────────────────────────────
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-cargo_tracker}"
DB_USER="${DB_USER:-cargo_app}"
DB_PASSWORD="${DB_PASSWORD:-change-me}"

POOL_NAME="cargoTrackerPool"
JNDI_NAME="jdbc/cargoTrackerDS"

# These names must match: the app's persistence.xml looks up JNDI_NAME above.
PG_DRIVER_VERSION="42.7.3"
PG_DRIVER_JAR="postgresql-${PG_DRIVER_VERSION}.jar"

# ── Locate asadmin ─────────────────────────────────────────────────────────
ASADMIN="asadmin"
if [[ -n "${GLASSFISH_HOME:-}" ]]; then
    ASADMIN="$GLASSFISH_HOME/bin/asadmin"
fi
if ! command -v "$ASADMIN" >/dev/null 2>&1; then
    echo "ERROR: cannot find asadmin." >&2
    echo "       Either add GlassFish bin/ to PATH or" >&2
    echo "       export GLASSFISH_HOME=/path/to/glassfish7" >&2
    exit 1
fi

# ── Locate the PostgreSQL driver in the local Maven repo ───────────────────
# Maven downloads it as a transitive dependency at build time, so by the time
# anyone runs this script the JAR is already on disk.
M2_REPO="${HOME}/.m2/repository/org/postgresql/postgresql/${PG_DRIVER_VERSION}/${PG_DRIVER_JAR}"
if [[ ! -f "$M2_REPO" ]]; then
    echo "ERROR: PostgreSQL driver not found at $M2_REPO" >&2
    echo "       Run 'mvn package' first to populate the local Maven repository." >&2
    exit 1
fi

# ── Get the GlassFish domain's lib dir ──────────────────────────────────────
# The driver MUST live in GlassFish's classpath, not just the WAR. The pool
# initialises before any deployed app, so it needs the driver class loadable
# at server-start time.
DOMAIN_DIR=$("$ASADMIN" __locations 2>/dev/null | grep "Domain Root" | awk '{print $NF}')
if [[ -z "${DOMAIN_DIR:-}" ]]; then
    echo "ERROR: 'asadmin __locations' did not return a Domain Root." >&2
    echo "       Is the domain running? Try: $ASADMIN start-domain" >&2
    exit 1
fi
DOMAIN_LIB="$DOMAIN_DIR/lib"

echo "→ Copying $PG_DRIVER_JAR into $DOMAIN_LIB"
mkdir -p "$DOMAIN_LIB"
cp -f "$M2_REPO" "$DOMAIN_LIB/"

# A library added to domain lib is only seen after a restart. The alternative
# is `asadmin add-library --type common` then `asadmin restart-domain` — same
# net effect, this one is one fewer command.
echo "→ Restarting GlassFish so the driver is on the server classpath"
"$ASADMIN" stop-domain  >/dev/null 2>&1 || true
"$ASADMIN" start-domain >/dev/null

# ── Idempotency: drop any prior pool/resource of these names ───────────────
# A failed previous run can leave the resource bound but the pool broken,
# blocking a fresh create. Wiping first keeps the script reusable.
echo "→ Removing any existing pool/resource (safe if absent)"
"$ASADMIN" delete-jdbc-resource         "$JNDI_NAME"  >/dev/null 2>&1 || true
"$ASADMIN" delete-jdbc-connection-pool  "$POOL_NAME"  >/dev/null 2>&1 || true

# ── Create the connection pool ─────────────────────────────────────────────
# datasourceclassname  PGSimpleDataSource — the canonical XA-capable pg DS.
# restype              javax.sql.DataSource matches the app's @Resource lookups.
# property             colon-separated key=value pairs (asadmin convention).
echo "→ Creating connection pool '$POOL_NAME'"
"$ASADMIN" create-jdbc-connection-pool \
    --datasourceclassname org.postgresql.ds.PGSimpleDataSource \
    --restype javax.sql.DataSource \
    --isconnectvalidatereq=true \
    --validationmethod=auto-commit \
    --property "ServerName=${DB_HOST}:PortNumber=${DB_PORT}:DatabaseName=${DB_NAME}:User=${DB_USER}:Password=${DB_PASSWORD}" \
    "$POOL_NAME" >/dev/null

# ── Create the JNDI resource the app looks up ──────────────────────────────
# This is the name that appears verbatim in persistence.xml as
# <jta-data-source>jdbc/cargoTrackerDS</jta-data-source>. Mismatch here is the
# #1 cause of "no datasource bound" errors at deploy time.
echo "→ Creating JDBC resource '$JNDI_NAME' → $POOL_NAME"
"$ASADMIN" create-jdbc-resource \
    --connectionpoolid "$POOL_NAME" \
    "$JNDI_NAME" >/dev/null

# ── Verify ─────────────────────────────────────────────────────────────────
echo "→ Pinging pool (this opens a real connection to PostgreSQL)…"
if "$ASADMIN" ping-connection-pool "$POOL_NAME"; then
    echo
    echo "OK. GlassFish is talking to PostgreSQL."
    echo "    Pool       : $POOL_NAME"
    echo "    JNDI       : $JNDI_NAME"
    echo "    Connecting : ${DB_USER}@${DB_HOST}:${DB_PORT}/${DB_NAME}"
    echo
    echo "Now redeploy the app:"
    echo "    mvn clean package"
    echo "    $ASADMIN deploy --force=true target/cargo-tracker.war"
else
    echo
    echo "FAILED. The pool exists but cannot reach the database. Check, in order:" >&2
    echo "  1. Is PostgreSQL actually running on $DB_HOST:$DB_PORT ?" >&2
    echo "       psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d $DB_NAME -c '\\q'" >&2
    echo "  2. Does the user '$DB_USER' exist with the password in this script?" >&2
    echo "  3. Does database '$DB_NAME' exist?  (createdb -U $DB_USER $DB_NAME)" >&2
    echo "  4. Is pg_hba.conf set to 'md5' or 'scram-sha-256' for $DB_USER ?" >&2
    exit 2
fi
