# Oracle ARM64 staging

The staging stack uses an Oracle Ampere ARM64 VM, Docker Engine with Compose v2, and a host Caddy service. Cloudflare terminates public HTTPS and reaches Caddy over HTTPS in Full (strict) mode. Caddy proxies to the web service on the VM's loopback interface. The web service reaches the API over an internal Docker network. Jena uses the API's named TDB2 volume. The API, TDB2, and RDF publication commands have no public listener.

## Build and start a pinned stack

1. Install Git, Temurin JDK 25, Docker Engine with Compose v2.24.4 or later, Caddy, curl, and jq. Use cgroup v2 with container memory limits enabled. Check out the web and API repositories in sibling directories named `curricula.live` and `api.curricula.live`.
2. Obtain a reviewed RDF publication and pin its full producer commit and manifest SHA-256. Verify the manifest hash against the approved digest. Place the manifest and its declared artifacts in a dedicated immutable directory outside either checkout. The container runs as UID 10001, so its user must be able to read the read-only bind mount. Modes 0755 for the publication directory and 0644 for the release's public artifacts suffice. The API independently validates every artifact and the declared named graphs before activation.
3. From the web checkout, set these variables in an untracked file with the pinned values. Deploy the `deploy/` files from the same commit used for `WEB_REVISION`.

   ```bash
   API_REVISION='<40-character-API-commit>'
   WEB_REVISION='<40-character-web-commit>'
   RDF_PACKAGE_DIR='/srv/curricula/releases/formal-sciences/<manifest-sha256>'
   RDF_MANIFEST_SHA256='<64-character-reviewed-manifest-sha256>'
   RDF_COMMIT='<40-character-producer-commit>'
   STAGING_PLATFORM=linux/arm64
   WEB_BIND_PORT=8080
   ```

4. Load that file and create images from the pinned commits.

   ```bash
   set -a
   source /srv/curricula/staging.env
   set +a
   printf '%s  %s\n' "$RDF_MANIFEST_SHA256" "$RDF_PACKAGE_DIR/manifest.json" | sha256sum -c -
   bash deploy/package.sh
   stack=(docker compose --env-file /srv/curricula/staging.env -f deploy/compose.yaml)
   "${stack[@]}" config --quiet
   "${stack[@]}" up --build -d --wait --wait-timeout 240
   "${stack[@]}" ps
   ```

   Packaging exports the exact Git trees, runs both Maven verification builds, and keeps staging JARs under the ignored `deploy/.build/` directory. Docker packages the same bytecode for either Linux architecture using a digest-pinned Temurin 25 runtime image. For local amd64 verification, set `STAGING_PLATFORM=linux/amd64`. `CURRICULA_MAVEN` may point to a local Maven launcher when the development environment requires a proxy or a preconfigured dependency cache. Use the repositories' Maven wrappers on a standard staging build host.

5. Check knowledge readiness and representative rendering. Choose a query known to have a result in the pinned release.

   ```bash
   "${stack[@]}" exec -T api curricula-healthcheck 8081 /api/v1/knowledge/publication '"available":true' |
     jq -e --arg commit "$RDF_COMMIT" --arg digest "$RDF_MANIFEST_SHA256" \
       'select(.sourceCommit == $commit and .manifestSha256 == $digest and .concepts > 0)'
   curl -fsS "http://127.0.0.1:$WEB_BIND_PORT/actuator/health" | jq -e '.status == "UP"'
   curl -fsS "http://127.0.0.1:$WEB_BIND_PORT/search/results?q=mathematics" | grep 'href="/concepts/'
   curl -fsS "http://127.0.0.1:$WEB_BIND_PORT/concepts/mathematics/graph/data?depth=1" |
     jq -e '(.nodes | length) > 0'
   ```

The API readiness probe gates web startup on an active RDF publication. Confirm the publication's commit and digest separately after a release change or recovery. Keep published staging images or rebuild from recorded Git commits. Development ontology fixtures may be used for an isolated smoke test. Promoting a publication to production requires separate data review.

## Archived graph search

To enable the separate computing archive in staging, run `bash scripts/fetch-graph-archive.sh /srv/curricula/releases/graph-archive.json` from the web checkout. Add `ARCHIVE_PACKAGE_PATH=/srv/curricula/releases/graph-archive.json` to the staging environment and append `-f deploy/compose.archive.yaml` to the Compose command. The archive is a pinned, provisional projection. Its links retain their original release-scoped identifiers.

## Origin HTTPS and observability

Use the host's `deploy/Caddyfile` as the staging Caddy configuration. Set `CURRICULA_STAGING_HOST` to the dedicated staging hostname. Set `CURRICULA_STAGING_CERT` and `CURRICULA_STAGING_KEY` to Caddy-readable certificate and key files outside Git. The certificate must be unexpired and trusted by Cloudflare for this hostname. Set `CURRICULA_WEB_BIND_PORT` to the Compose loopback port if it differs from 8080. Supply these settings to the Caddy service, validate the configuration, and reload Caddy. On a shared host, add the staging site block to the existing configuration and preserve its global settings. Caddy blocks Actuator routes, while Compose health checks reach them directly.

On the staging host, allow TCP 443 only from Cloudflare's current published origin IP ranges, and restrict SSH to trusted administration sources. Enforce those restrictions in the OCI security list or network security group and the host firewall. Check the staging hostname through its intended proxy and certificate chain. Keep dynamic and authenticated routes uncached. Configure Cloudflare Access or equivalent access restrictions if the staging site is private. Staging settings apply to a separate staging hostname.

The Compose API and web services use 3 GiB and 1 GiB container memory budgets. JDK heap sizing uses 60% of each budget. Adjust `API_MEMORY_LIMIT` and `WEB_MEMORY_LIMIT` (for example `3g` or `1024m`) together with capacity on the VM. Inspect each container's `/sys/fs/cgroup/memory.max` to verify the running limit and leave room for the container runtime, Caddy and filesystem cache. Review `docker compose ps`, `docker compose logs -f api web`, `docker stats`, and Caddy's access logs. Alert on sustained unhealthy status, upstream 5xx, VM memory pressure, disk growth and certificate expiry. Compose rotates each service's logs at 10 MiB with three files.

## Backup and recover TDB2

Stop both services before copying TDB2 to flush outstanding writes. Coordinate a staging maintenance window.

```bash
backup=/srv/curricula/backups/tdb2-$(date -u +%Y%m%dT%H%M%SZ).tar
umask 077
"${stack[@]}" stop web
"${stack[@]}" stop api
"${stack[@]}" run --rm --no-deps -T --entrypoint tar api \
  --sparse -cpf - -C /var/lib/curricula/rdf . > "$backup"
tar -tf "$backup" > "$backup.files"
sha256sum "$backup" > "$backup.sha256"
"${stack[@]}" up --no-recreate --no-build -d --wait --wait-timeout 240
```

Store the verified backup off the VM alongside the pinned deployment metadata and immutable release. Restore only trusted, verified archives. Restore into a fresh Compose project and TDB2 volume before deciding on a recovery switch. Use a unique project name and unused loopback port.

```bash
sha256sum -c "$backup.sha256"
export WEB_BIND_PORT=18081
restore=("${stack[@]}" --project-name curricula-staging-restore)
restore+=(-f deploy/compose.persisted.yaml)
"${restore[@]}" run --rm --no-deps -T --entrypoint tar api \
  -xpf - -C /var/lib/curricula/rdf < "$backup"
"${restore[@]}" up --no-build -d --wait --wait-timeout 240
```

The override starts the API from persistent state and removes the import package mount. It can restore from a verified backup when the package directory is unavailable. Repeat the publication and web smoke checks against the recovery project and port. Use the source commit and manifest digest recorded with the backup. Keep the original volume until the replacement passes the recovery checks and staging cutover has been approved.

To roll back an activation on an API revision that provides the offline `rdf-rollback` command, stop web and API, run the command once, and start with the same persisted-state override:

```bash
"${stack[@]}" stop web
"${stack[@]}" stop api
persisted=("${stack[@]}" -f deploy/compose.persisted.yaml)
"${persisted[@]}" run --rm --no-deps -T api rdf-rollback
"${persisted[@]}" up --no-build -d --wait --wait-timeout 240
```

Confirm the previously active publication's commit and manifest digest. Keep the override enabled until the explicitly reviewed activation of another package.
