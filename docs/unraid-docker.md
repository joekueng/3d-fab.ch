# Unraid: container identity and Docker image maintenance

## Dedicated application identity

The backend image defines `printcalc` with UID/GID `10001:10001`; deployment
Compose and the storage preflight use the same numbers. This is a candidate
dedicated identity, not a claim that these numbers are unused on your server.
`UID` identifies a Unix user; it is not a UUID. Avoid Unraid's shared
`nobody:users` identity (`99:100`) when application isolation is the goal.

Before migrating, run these read-only commands in the Unraid terminal:

```bash
getent passwd 10001
getent group 10001
docker ps -aq | xargs -r docker inspect --format '{{.Name}} user={{.Config.User}}'
docker ps -aq | xargs -r docker inspect --format '{{.Name}} {{range .Config.Env}}{{println .}}{{end}}' | sed -n '/^\//p; /^PUID=/p; /^PGID=/p'
```

The last command prints container names and only PUID/PGID environment settings.
Named image users and entrypoints that switch users also require checking the
corresponding application documentation or `docker exec <container> id`.
If another application already uses 10001, choose a free pair and change the
backend Dockerfile (including directory ownership), both Compose files and
deployment preflight consistently before rebuilding. Merely setting PUID/PGID
does not change this image's user.

There is no need to create a share user in Unraid's Users screen for this
container. The account exists inside the image; Linux bind-mount permissions
use numeric UID/GID. Unraid share users manage access to network shares.
A different UID reduces access through filesystem permissions; continue to
mount only the directories each application needs.

Back up storage and stop the backend for the selected environment before
changing ownership. Follow the exact five-directory migration in the
[backend README](../backend/README.md#backend-container-user-and-storage-permissions).
Do not change all of `/mnt/cache/appdata` or apply `chmod -R 777`. Preserve the
external proxy's read/traverse access to public media. The deployment script
checks storage access before replacing the backend. After deployment, verify:

```bash
docker exec print-calculator-backend-dev id
```

Expected: UID/GID 10001. Repeat the documented upload/slicing checks and use
`int` or `prod` for their respective environments. This migration concerns the
backend; PostgreSQL, ClamAV and other images retain their own supported users.

## Recover leftover E2E stacks

Each test run creates its own `e2e-printcalc-e2e-…` Compose project. Inventory
in Unraid can include both an active test run and abandoned runs; a running
container alone does not distinguish them. Pause E2E jobs and check the Gitea job
history before deleting an old project. In a checkout with the updated helpers:

```bash
bash scripts/e2e/cleanup-stack.sh --list
bash scripts/e2e/cleanup-stack.sh --project e2e-printcalc-e2e-ofd0sw
```

The first command discovers projects from container, volume and network labels;
the second previews the resources of one project. After checking the preview,
remove that abandoned test project, including its disposable database:

```bash
bash scripts/e2e/cleanup-stack.sh --project e2e-printcalc-e2e-ofd0sw --delete
```

Repeat for each confirmed abandoned project. The helper needs only Bash and
Docker, so it can also be copied to Unraid and invoked directly with `bash`.
It requires an explicit harness project for deletion, selects resources by exact
Compose project label, and removes that run's backend/frontend/proxy image tags.
It does not use global Docker pruning. It reports removal failures and is safe
to repeat. Old unlabelled browser containers with random names need separate
inspection; their names alone do not establish that they belong to E2E.

The harness records the project in ignored `.e2e-project` before startup, reports
Compose cleanup errors and retries cleanup by labels. Both CI workflows also
run this helper in an `always()` step using that manifest. New browser containers
carry the same project label, allowing cleanup even when their process is killed.
No shell trap or workflow step can run after a host/runner crash or forced
termination of the whole job container; use the explicit recovery command then.
Never automatically sweep every E2E project while other jobs may be running.

Run `node --test scripts/e2e/cleanup-stack.test.mjs` to verify project isolation,
preview mode, repeat cleanup, visible failures and cleanup after partial startup
with a simulated Docker CLI. These tests do not mutate a Docker daemon.

Docker CLI options were checked against the official
[container filters](https://docs.docker.com/reference/cli/docker/container/ls/),
[container labels](https://docs.docker.com/reference/cli/docker/container/create/),
[volume filters](https://docs.docker.com/reference/cli/docker/volume/ls/),
[network filters](https://docs.docker.com/reference/cli/docker/network/ls/), and
[Compose teardown](https://docs.docker.com/reference/cli/docker/compose/down/)
documentation on 2026-11-13. No package or image version changes are required.

## Recover the old E2E images

Pause E2E jobs first. No repository checkout is needed on Unraid. Paste this
function directly into the Unraid Web Terminal, then run the preview:

```bash
cleanup_printcalc_images() (
  set -euo pipefail
  mode="${1:---list}"
  [[ "$mode" == --list || "$mode" == --delete ]] || return 2
  images="$(docker image ls --format '{{.Repository}}:{{.Tag}}')"
  while IFS= read -r tag; do
    [[ "$tag" =~ ^e2e-printcalc-e2e-[a-z0-9-]+-(backend|frontend|proxy):latest$ ]] || continue
    image_id="$(docker image inspect --format '{{.Id}}' "$tag")"
    containers="$(docker ps -aq --filter "ancestor=$image_id")"
    if [[ -n "$containers" ]]; then
      printf 'IN USE: %s\n' "$tag"
    elif [[ "$mode" == --delete ]]; then
      docker image rm "$tag"
    else
      printf 'REMOVABLE: %s\n' "$tag"
    fi
  done <<< "$images"
)
cleanup_printcalc_images --list
```

In the same terminal session, after reviewing the preview:

```bash
cleanup_printcalc_images --delete
docker system df
```

When a checkout is available, the equivalent maintained helper is
`bash scripts/e2e/cleanup-images.sh --list` / `--delete`.

The default is a preview. Deletion selects only generated
`e2e-printcalc-e2e-…-{backend,frontend,proxy}:latest` tags and skips images
referenced by any running or stopped container. It uses no force flag, removes
no containers or volumes, and leaves the Gitea runner image alone. Multiple
images share layers, so adding their displayed sizes does not predict reclaimed
space. If a leftover E2E stack needs removal, inspect its containers and Compose
project separately before stopping it; this helper does not do that.

New runs remove their own three generated image tags during cleanup. Deploy
and CI now prune only dangling images carrying the project label and older
than seven days. This does not remove tagged deployment images or build cache.
Old images without the new label are not covered by that routine. Inspect build
cache separately with `docker builder du`; its cleanup needs a dedicated builder
or an explicitly chosen maintenance operation on a shared Docker daemon.

Official references: [Compose down](https://docs.docker.com/reference/cli/docker/compose/down/),
[image prune](https://docs.docker.com/reference/cli/docker/image/prune/),
[Unraid users](https://docs.unraid.net/unraid-os/system-administration/secure-your-server/user-management/).
