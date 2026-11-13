# Security policy

Thank you for helping keep MapsCtrl and its users safe.

## Reporting a problem

**Please don't report security problems in a public issue.** Report them privately instead:

1. Go to the [Security tab](https://github.com/VoidedKN0X/MapsCtrl/security) of this repo.
2. Click **Report a vulnerability** and describe what you found, how to reproduce it, and which version of MapsCtrl you used.

Only the maintainer can see the report. I'll reply as soon as I can, work on a fix, and credit you in the changelog if you'd like.

## Supported versions

Only the latest release gets security fixes. Please update before reporting, and check the [changelog](CHANGELOG.md) to see whether it's already fixed.

## What counts

MapsCtrl has no server and no account. It only goes online for downloads you start and, if you set it up, ETA sharing to your own ntfy server. So these count:

- Any way for MapsCtrl to send data off the phone that isn't one of those, or to send more than it says (for example your position or searches)
- Another app reading MapsCtrl's saved places, tracks, maps or settings
- A crafted map, search-data, routing-data or GPX file making MapsCtrl run code, read files it shouldn't, or crash in a way that loses your data
- Downloads that could be redirected to or replaced by another server
- Anything that lets someone run code or read files they shouldn't

## How releases are protected

Every release is built from this repo by GitHub Actions and signed with the same release key. Its SHA-256 certificate fingerprint is:

```
68:77:8C:2C:AF:00:30:9D:95:74:4D:2D:70:F0:7C:EF:2A:9F:D8:5F:F1:0D:B0:D5:B4:78:0E:AB:25:A1:B6:92
```
