# Public read-only staging

The staging profile packages API `d4f2ec98a600f9ca99f277257ef03ae8f4d7f76c` and web `95f920fd334b2d223214de73c83064458f520b99` from Git archives. The first start downloads the Formal Sciences v0.8.0 development fixture at `17c4c1db6901381b41bd377fb8c24c2f868c1f23`. It checks the manifest SHA-256 `25152ce02ee0b6b0cf6039b74d9d5d41cb058571cb05097845d86eb88bbaf8ba` and uses the API's offline importer to verify every artifact and activate the publication. This fixture is for isolated staging. Production requires a separately reviewed release.

## Prerequisites

Use a Linux host with Docker Engine 28 or newer so loopback port publishing stays private on the local network. Use Compose v2.24.4 or newer, JDK 25, Git, Bash, curl, jq, GNU coreutils and tar, and flock. The first build needs outbound HTTPS to GitHub, Maven Central, and Docker Hub. Use a cgroup-enabled host for the configured API and web memory limits and a local Docker volume supporting file locks, atomic renames, and directory syncing.

On the staging host, clone the two checkouts as siblings. Access is through the authorized GitHub account for the host.

```sh
git clone -b feat/rdf-release-consumer https://github.com/curricula-live/api.curricula.live.git
git clone -b feat/rdf-client-integration https://github.com/curricula-live/curricula.live.git
cd curricula.live
```

## Start on x86-64 or ARM64

On the **x86-64 tower** host, configure once.

```sh
./deploy/configure curricula-tower 8080 linux/amd64
```

On **Oracle Ampere ARM64**, configure once instead.

```sh
./deploy/configure curricula-oracle 8080 linux/arm64
```

On any supported Linux host, `./deploy/configure curricula-staging 8080` can select the architecture automatically. Then use the same commands on either architecture.

```sh
./deploy/up
./deploy/check
curl -fsS 'http://127.0.0.1:8080/search/results?q=DPLL&category=concepts'
curl -fsS 'http://127.0.0.1:8080/concepts/dpll-algorithm'
```

The first command builds the pinned images as needed and initializes a named TDB2 volume. The long-running containers open the volume through `compose.persisted.yaml`. PostgreSQL, identity services and extra publishers are optional profiles. The API has no published ports. Web HTTP binds to `127.0.0.1:8080` for local checks or an authorized SSH tunnel. The readiness gate requires a loaded RDF publication. Without one, the private API reports its bootstrap state and knowledge requests return HTTP 503.

```sh
./deploy/down
./deploy/up
```

`down` removes the project's containers and networks while retaining the named volume. A subsequent `up` reuses the selected release without downloading or mounting the fixture.

## Backup and restore

Choose a new backup filename. Backup stops the services for a consistent filesystem snapshot and resumes the stack if it was running. Keep the archive and checksum together.

```sh
mkdir -p deploy/.staging/backups
backup="$PWD/deploy/.staging/backups/formal-sciences.tar"
./deploy/backup "$backup"
```

To rehearse recovery, choose a fresh Compose project and unused loopback port. Use this config path for all restore project commands.

```sh
export CURRICULA_STAGING_CONFIG="$PWD/deploy/.staging/restore.env"
./deploy/configure curricula-restore 8081
./deploy/restore "$backup"
./deploy/check
./deploy/down
unset CURRICULA_STAGING_CONFIG
```

Restore verifies the checksum and requires a project with no RDF volume. Use trusted backups from this staging profile. The restore reads its archive and does not require the original import files.

## Optional HTTPS ingress

After approving a staging hostname and origin certificate, run the existing Caddy configuration on the same host. Set `CURRICULA_STAGING_HOST` to that hostname, `CURRICULA_STAGING_CERT` and `CURRICULA_STAGING_KEY` to its certificate and private key files, and `CURRICULA_WEB_BIND_PORT` to the configured loopback port.

```sh
caddy validate --config deploy/Caddyfile
caddy run --config deploy/Caddyfile
```

Caddy forwards only to the loopback web application and blocks Actuator paths. For a future Cloudflare-proxied hostname, use Full (strict) origin TLS and restrict inbound HTTPS to Cloudflare's published origin ranges. Local staging runs directly on loopback. See the [extended runbook](docs/deployment.md) for optional profiles and service configuration.
