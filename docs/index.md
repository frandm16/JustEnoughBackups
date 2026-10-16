---
layout: home

hero:
  name: "Just Enough Backups"
  text: "Backups you can trust."
  tagline: "A fast, Fabric-native backup system for Minecraft servers. Automate snapshots, protect incremental chains, and restore safely from an in-game interface."
  actions:
    - theme: brand
      text: Quick Start
      link: /getting-started
    - theme: alt
      text: Commands
      link: /commands

features:
  - title: Full, partial & differential
    details: Create complete snapshots or save only what changed. Dependency-aware retention keeps every required base backup safe.
  - title: Fast by design
    details: Parallel scanning, compression, and restore extraction make large worlds quicker to protect and recover.
  - title: Scheduling that fits your server
    details: Set independent intervals, choose start or stop backups, and pause scheduled work when nobody is online.
  - title: A safer restore workflow
    details: Verify the selected backup chain in staging and create a safety backup before applying a restore.
  - title: Remote copies
    details: Send verified archives to S3-compatible storage or WebDAV with retries and SHA-256 checks.
  - title: Manage it in-game
    details: Browse, filter, rename, delete, and restore backups through a clear server-friendly interface.
---

<style>
:root {
  --vp-home-hero-name-color: transparent;
  --vp-home-hero-name-background: -webkit-linear-gradient(120deg, #d50ffb 30%, #a813e2);
}
</style>
