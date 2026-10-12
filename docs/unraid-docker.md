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
