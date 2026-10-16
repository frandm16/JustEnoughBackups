<script setup lang="ts">
import { onMounted, onUnmounted } from 'vue'

type UmamiTracker = {
  track: (eventName: string) => void
}

const slugify = (value: string) => value
  .toLowerCase()
  .normalize('NFD')
  .replace(/[\u0300-\u036f]/g, '')
  .replace(/[^a-z0-9]+/g, '-')
  .replace(/^-|-$/g, '')

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

  const host = slugify(destination.hostname.replace(/^www\./, '').split('.')[0]).slice(0, 12) || 'external'
  const pathSegments = destination.pathname.split('/').filter(Boolean)
  const target = slugify(pathSegments[pathSegments.length - 1] || '')
    || slugify(link.innerText)
    || 'home'
  const prefix = `outbound-${host}-`
  const suffix = `-${area}`
  const eventName = `${prefix}${target.slice(0, 50 - prefix.length - suffix.length)}${suffix}`

  tracker?.track(eventName)
}

onMounted(() => document.addEventListener('click', trackOutboundLink))
onUnmounted(() => document.removeEventListener('click', trackOutboundLink))
</script>

<template></template>
