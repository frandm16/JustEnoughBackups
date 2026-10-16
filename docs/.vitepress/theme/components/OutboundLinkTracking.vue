<script setup lang="ts">
import { onMounted, onUnmounted } from 'vue'

type UmamiTracker = {
  track: (eventName: string) => void
}

const slugify = (value: string) => value
  .replace(/([a-z0-9])([A-Z])/g, '$1-$2')
  .toLowerCase()
  .normalize('NFD')
  .replace(/[\u0300-\u036f]/g, '')
  .replace(/[^a-z0-9]+/g, '-')
  .replace(/^-|-$/g, '')

const canonicalize = (value: string) => {
  const slug = slugify(value)
  const aliases: Record<string, string> = {
    'justenoughbackups': 'just-enough-backups',
    'justenoughbackups-jeb': 'just-enough-backups',
    'modmenu': 'mod-menu',
    '7-zip': '7zip'
  }
  return aliases[slug] || slug
}

const trackOutboundLink = (event: MouseEvent) => {
  if (!(event.target instanceof Element)) return

  const link = event.target.closest<HTMLAnchorElement>('a[href]')
  if (!link || link.hasAttribute('data-umami-event')) return

  let destination: URL
  try {
    destination = new URL(link.href, window.location.href)
  } catch {
    return
  }

  if (!['http:', 'https:'].includes(destination.protocol)) return
  if (destination.origin === window.location.origin) return

  const tracker = (window as Window & { umami?: UmamiTracker }).umami
  const area = link.closest('.VPNavBar')
    ? 'header'
    : link.closest('.VPFooter')
      ? 'footer'
      : link.closest('.VPHome')
        ? 'landing'
        : 'content'

  const host = canonicalize(destination.hostname.replace(/^www\./, '').split('.')[0]).slice(0, 16) || 'external'
  const pathSegments = destination.pathname.split('/').filter(Boolean)
  const target = canonicalize(pathSegments[pathSegments.length - 1] || '')
    || canonicalize(link.innerText)
    || 'home'
  const eventName = target === host
    ? `${host}-${area}`
    : `${host}-${target.slice(0, 50 - host.length - area.length - 2)}-${area}`

  tracker?.track(eventName)
}

onMounted(() => document.addEventListener('click', trackOutboundLink))
onUnmounted(() => document.removeEventListener('click', trackOutboundLink))
</script>

<template></template>
