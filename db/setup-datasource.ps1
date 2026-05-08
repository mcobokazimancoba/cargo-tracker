<#
.SYNOPSIS
    Recreates the GlassFish-side JDBC plumbing the app expects.

.DESCRIPTION
    PowerShell port of db/setup-datasource.sh — for Windows users without
    Git Bash. Same idempotent logic:
      - copies the PostgreSQL driver into GlassFish's domain lib
      - restarts the domain so the driver loads
      - drops any prior pool/resource by the same names
      - creates the connection pool      (cargoTrackerPool)
      - creates the JNDI JDBC resource   (jdbc/cargoTrackerDS)
      - pings the pool to verify

    The JNDI name MUST stay 'jdbc/cargoTrackerDS' because that's what
    persistence.xml looks up; changing it here breaks the app at deploy.

.EXAMPLE
    $env:DB_PASSWORD = 'CargoApp2025'
    .\db\setup-datasource.ps1

.EXAMPLE
    $env:GLASSFISH_HOME = 'C:\glassfish7'
    $env:DB_PASSWORD    = 'CargoApp2025'
    .\db\setup-datasource.ps1
#>

#Requires -Version 5.1
[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'

# PowerShell 7+ promotes any stderr output from a native command to a
# terminating error when $ErrorActionPreference = 'Stop'. asadmin writes a
# LOT to stderr even on success (the whole Java launch command line on
# start-domain, "resource does not exist" during idempotent cleanup, etc.).
# The opt-out variable below works in some PS 7 builds but is unreliable
# across versions, so the real defence is the Invoke-AsAdmin helper below
# which wraps every call in try/catch and checks $LASTEXITCODE for the real
# success/failure signal. Safe no-op on PS 5.1.
$PSNativeCommandUseErrorActionPreference = $false

# ── Helper: run asadmin and survive PS 7's strict stderr handling ──────────
function Invoke-AsAdmin {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)]                     [string]   $Description,
        [Parameter(ValueFromRemainingArguments)]   [string[]] $AsAdminArgs = @(),
        [switch] $IgnoreFailure
    )
    try {
        & $AsAdmin @AsAdminArgs *>&1 | Out-Null
    } catch {
        # PS 7 raised a terminating error from asadmin's stderr writes.
        # The actual success/failure is in $LASTEXITCODE; let the check
        # below decide whether this matters.
    }
    if (-not $IgnoreFailure -and $LASTEXITCODE -ne 0) {
        Write-Host ""
        Write-Host "ERROR: $Description failed (exit code $LASTEXITCODE)." -ForegroundColor Red
        Write-Host "       Check the GlassFish log for details:"           -ForegroundColor Red
        Write-Host "         <glassfish-install>\glassfish\domains\domain1\logs\server.log" -ForegroundColor Red
        exit 1
    }
}

# ── Configuration (env vars override defaults) ─────────────────────────────
$DbHost     = if ($env:DB_HOST)     { $env:DB_HOST }     else { 'localhost' }
$DbPort     = if ($env:DB_PORT)     { $env:DB_PORT }     else { '5432' }
$DbName     = if ($env:DB_NAME)     { $env:DB_NAME }     else { 'cargo_tracker' }
$DbUser     = if ($env:DB_USER)     { $env:DB_USER }     else { 'cargo_app' }
$DbPassword = if ($env:DB_PASSWORD) { $env:DB_PASSWORD } else { 'change-me' }

$PoolName = 'cargoTrackerPool'
$JndiName = 'jdbc/cargoTrackerDS'

$PgDriverVersion = '42.7.3'
$PgDriverJar     = "postgresql-$PgDriverVersion.jar"

# ── Locate asadmin.bat ─────────────────────────────────────────────────────
function Find-AsAdmin {
    # Honour an explicit override first
    if ($env:GLASSFISH_HOME) {
        $candidate = Join-Path $env:GLASSFISH_HOME 'bin\asadmin.bat'
        if (Test-Path $candidate) { return $candidate }
    }

    # Then look on PATH
    $cmd = Get-Command 'asadmin.bat' -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }

    # Then scan a few common install locations
    $candidates = @(
        'C:\glassfish7\bin\asadmin.bat',
        'C:\glassfish\bin\asadmin.bat',
        'C:\Program Files\GlassFish7\bin\asadmin.bat',
        'C:\Program Files\glassfish7\bin\asadmin.bat',
        'C:\Program Files\Eclipse Foundation\glassfish7\bin\asadmin.bat',
        'C:\Program Files (x86)\GlassFish7\bin\asadmin.bat'
    )
    foreach ($c in $candidates) {
        if (Test-Path $c) { return $c }
    }
    return $null
}

$AsAdmin = Find-AsAdmin
if (-not $AsAdmin) {
    Write-Host ""
    Write-Host "ERROR: cannot find asadmin.bat anywhere I looked." -ForegroundColor Red
    Write-Host "       Set the install path explicitly and re-run:" -ForegroundColor Red
    Write-Host '         $env:GLASSFISH_HOME = ''C:\path\to\glassfish7''' -ForegroundColor Yellow
    Write-Host '         .\db\setup-datasource.ps1'                         -ForegroundColor Yellow
    exit 1
}
Write-Host "→ Using asadmin: $AsAdmin"

# ── Locate the PostgreSQL driver in the local Maven repo ───────────────────
# Maven downloads it as a dependency at build time, so by the time anyone
# runs this script the JAR is already on disk under ~/.m2.
$M2Driver = Join-Path $env:USERPROFILE ".m2\repository\org\postgresql\postgresql\$PgDriverVersion\$PgDriverJar"
if (-not (Test-Path $M2Driver)) {
    Write-Host "ERROR: PostgreSQL driver not found at $M2Driver" -ForegroundColor Red
    Write-Host "       Run 'mvn package' first to populate the local Maven repo." -ForegroundColor Red
    exit 1
}

# ── Make sure the domain is running ────────────────────────────────────────
# `start-domain` is idempotent in practice: exits 0 if the domain is already
# up, otherwise starts it. We need the DAS up before any of the pool-creation
# commands below will work.
Write-Host "→ Ensuring GlassFish domain is running"
Invoke-AsAdmin -Description "ensure GlassFish domain is up" -IgnoreFailure start-domain

# ── Find the GlassFish domain's lib dir ────────────────────────────────────
# The driver MUST live in GlassFish's classpath (not just the WAR), because
# the pool initialises before any app is deployed.
#
# Derive the domain dir from the asadmin path rather than asking the DAS
# (`asadmin __locations`) — the output format of __locations changes between
# GlassFish versions and isn't stable to parse. The on-disk layout, by
# contrast, is fixed: domains live at one of two well-known relative paths
# depending on which of the two `asadmin.bat` files we picked up.
$AsAdminBin  = Split-Path $AsAdmin   -Parent     # …\bin
$AsAdminRoot = Split-Path $AsAdminBin -Parent    # …\glassfish7  OR  …\glassfish7\glassfish

$CandidateDomains = @(
    (Join-Path $AsAdminRoot 'glassfish\domains\domain1'),  # if asadmin is the top-level wrapper
    (Join-Path $AsAdminRoot 'domains\domain1')             # if asadmin is the inner module launcher
)

$DomainDir = $CandidateDomains | Where-Object { Test-Path $_ } | Select-Object -First 1

if (-not $DomainDir) {
    Write-Host "ERROR: cannot locate the GlassFish domain1 directory."  -ForegroundColor Red
    Write-Host "       Tried these paths:"                              -ForegroundColor Red
    $CandidateDomains | ForEach-Object { Write-Host "         $_"       -ForegroundColor Red }
    exit 1
}
Write-Host "→ Using domain dir: $DomainDir"
$DomainLib = Join-Path $DomainDir 'lib'

Write-Host "→ Copying $PgDriverJar into $DomainLib"
New-Item -ItemType Directory -Force -Path $DomainLib | Out-Null
Copy-Item -Force $M2Driver $DomainLib

# A library added to domain lib only takes effect after a domain restart.
# stop-domain is allowed to fail (-IgnoreFailure) because if the domain
# wasn't running yet we still want to start it cleanly below.
Write-Host "→ Restarting GlassFish so the driver is on the server classpath"
Invoke-AsAdmin -Description "stop GlassFish (might already be down)" -IgnoreFailure stop-domain
Invoke-AsAdmin -Description "start GlassFish with the new driver"                   start-domain

# ── Drop any prior pool/resource (idempotent) ──────────────────────────────
# delete-* commands fail when nothing is there to delete; that's the happy
# path on a clean install. -IgnoreFailure swallows it.
Write-Host "→ Removing any existing pool/resource (safe if absent)"
Invoke-AsAdmin -Description "delete old JDBC resource"     -IgnoreFailure delete-jdbc-resource         $JndiName
Invoke-AsAdmin -Description "delete old connection pool"   -IgnoreFailure delete-jdbc-connection-pool  $PoolName

# ── Create the connection pool ─────────────────────────────────────────────
# datasourceclassname  PGSimpleDataSource — the canonical pg DataSource.
# restype              javax.sql.DataSource matches the app's lookups.
# property             colon-separated key=value pairs (asadmin convention).
# Built via -f to avoid PowerShell variable-expansion quirks with embedded
# colons (e.g. "$Var:Suffix" parses as a scoped variable reference).
Write-Host "→ Creating connection pool '$PoolName'"
$Property = 'ServerName={0}:PortNumber={1}:DatabaseName={2}:User={3}:Password={4}' `
            -f $DbHost, $DbPort, $DbName, $DbUser, $DbPassword

Invoke-AsAdmin -Description "create JDBC connection pool" `
    create-jdbc-connection-pool `
    --datasourceclassname org.postgresql.ds.PGSimpleDataSource `
    --restype javax.sql.DataSource `
    --isconnectvalidatereq=true `
    --validationmethod=auto-commit `
    --property $Property `
    $PoolName

# ── Create the JNDI resource the app looks up ──────────────────────────────
# This is the name that appears verbatim in persistence.xml as
# <jta-data-source>jdbc/cargoTrackerDS</jta-data-source>. Mismatch here is
# the #1 cause of "no datasource bound" errors at deploy time.
Write-Host "→ Creating JDBC resource '$JndiName' → $PoolName"
Invoke-AsAdmin -Description "create JNDI resource" `
    create-jdbc-resource `
    --connectionpoolid $PoolName `
    $JndiName

# ── Verify ─────────────────────────────────────────────────────────────────
# We DO want to see the ping output (success or failure message), so this
# one doesn't go through Invoke-AsAdmin. try/catch + 2>&1 | Out-Host is
# enough to survive PS 7 strictness while still printing what asadmin says.
Write-Host "→ Pinging pool (this opens a real connection to PostgreSQL)…"
try { & $AsAdmin ping-connection-pool $PoolName 2>&1 | ForEach-Object { Write-Host $_ } }
catch { Write-Host $_ -ForegroundColor Red }
if ($LASTEXITCODE -eq 0) {
    Write-Host ""
    Write-Host "OK. GlassFish is talking to PostgreSQL." -ForegroundColor Green
    Write-Host "    Pool       : $PoolName"
    Write-Host "    JNDI       : $JndiName"
    Write-Host "    Connecting : ${DbUser}@${DbHost}:${DbPort}/${DbName}"
    Write-Host ""
    Write-Host "Now redeploy the app:"
    Write-Host "    mvn clean package"
    Write-Host "    & '$AsAdmin' deploy --force=true target\cargo-tracker.war"
} else {
    Write-Host ""
    Write-Host "FAILED. The pool exists but cannot reach the database. Check, in order:" -ForegroundColor Red
    Write-Host "  1. Is PostgreSQL actually running on ${DbHost}:${DbPort} ?"
    Write-Host "       psql -h $DbHost -p $DbPort -U $DbUser -d $DbName -c 'SELECT 1'"
    Write-Host "  2. Does the user '$DbUser' exist with the password set in `$env:DB_PASSWORD ?"
    Write-Host "  3. Does database '$DbName' exist?"
    exit 2
}
