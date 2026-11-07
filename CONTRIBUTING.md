# Contributing

## Branches
- `main` is always buildable and tested on two phones.
- Work on `feature/mX-short-name` or `fix/short-name`, open a PR into `main`.

## Commits (conventional)
`feat:`, `fix:`, `docs:`, `ci:`, `refactor:`, `test:`, `chore:` — e.g. `feat(net): add TCP frame codec`.

## Pull requests
CI (build + unit tests) must be green. Describe what changed and how it was tested on phones.

## Releases
Update CHANGELOG.md, merge, then `git tag v0.X.0 && git push origin v0.X.0`. The Release workflow publishes the APK.

## Signing
The repo contains a shared **debug** keystore (`debug.keystore`, password `android`) only so every
build has the same signature and updates install over each other. It is not a secret and must not be
used for a Play Store release.
