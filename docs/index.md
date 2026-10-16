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
    icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M4 7h16v13H4z"/><path d="M3 4h18v3H3zM9 12h6M9 16h6"/></svg>'
    details: Create complete snapshots or save only what changed. Dependency-aware retention keeps every required base backup safe.
  - title: Fast by design
    icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M13.5 2.8 5.8 13h5.1l-.5 8.2L18.2 11h-5.1l.4-8.2Z"/></svg>'
    details: Parallel scanning, compression, and restore extraction make large worlds quicker to protect and recover.
  - title: Scheduling that fits your server
    icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="3.5" y="5" width="17" height="16" rx="2.5"/><path d="M7.5 3v4M16.5 3v4M3.5 9.5h17M8 13h2M14 13h2M8 17h2"/></svg>'
    details: Set independent intervals, choose start or stop backups, and pause scheduled work when nobody is online.
  - title: A safer restore workflow
    icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M3.5 11a8.5 8.5 0 1 1 2.2 5.7"/><path d="M3.5 5.5V11h5.5M9 12.5l2 2 4-4"/></svg>'
    details: Verify the selected backup chain in staging and create a safety backup before applying a restore.
  - title: Remote copies
    icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M6.5 18.5h11a4 4 0 0 0 .4-8 6.5 6.5 0 0 0-12.4-1.1 4.4 4.4 0 0 0 1 9.1Z"/><path d="m9 13 3-3 3 3M12 10v8"/></svg>'
    details: Send verified archives to S3-compatible storage or WebDAV with retries and SHA-256 checks.
  - title: Manage it in-game
    icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="3" y="5" width="18" height="14" rx="3"/><path d="M7 12h4M9 10v4M16 11h.01M18 13h.01"/></svg>'
    details: Browse, filter, rename, delete, and restore backups through a clear server-friendly interface.
---

<style>
:root {
  --vp-home-hero-name-color: transparent;
  --vp-home-hero-name-background: -webkit-linear-gradient(120deg, #d50ffb 30%, #a813e2);
}
</style>
