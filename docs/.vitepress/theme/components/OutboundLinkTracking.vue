<script setup lang="ts">
import { onMounted, onUnmounted } from 'vue'

type UmamiTracker = {
  track: (eventName: string, eventData: Record<string, string>) => void
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

  tracker?.track('outbound-link-click', {
    destination: `${destination.origin}${destination.pathname}`,
    text: link.innerText.trim().slice(0, 100),
    area
  })
}

onMounted(() => document.addEventListener('click', trackOutboundLink))
onUnmounted(() => document.removeEventListener('click', trackOutboundLink))
</script>

<template></template>
