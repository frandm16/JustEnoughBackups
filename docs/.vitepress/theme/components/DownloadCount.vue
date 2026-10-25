<script setup>
import { onMounted, ref } from 'vue'

const total = ref(null)
const unavailable = ref(false)

onMounted(async () => {
  try {
    const response = await fetch('https://downloadscount.dmfran16.workers.dev')
    if (!response.ok) {
      unavailable.value = true
      return
    }

    const data = await response.json()
    if (typeof data.total === 'number' && Number.isFinite(data.total)) {
      total.value = data.total
    } else {
      unavailable.value = true
    }
  } catch {
    unavailable.value = true
  }
})
</script>

<template>
  <div class="download-count" aria-live="polite">
    <span class="download-count-label">Downloads across Modrinth + CurseForge</span>
    <strong v-if="total !== null" class="download-count-value">
      {{ total >= 1_000_000 ? `${(total / 1_000_000).toFixed(1)}M` : `${(total / 1_000).toFixed(1)}K` }}
    </strong>
    <span v-else-if="unavailable" class="download-count-value">Temporarily unavailable</span>
    <span v-else class="download-count-value">Loading…</span>
  </div>
</template>

<style scoped>
.download-count {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.25rem;
  margin: 1.25rem auto 0;
  color: var(--vp-c-text-2);
  text-align: center;
}

.download-count-label {
  font-size: 0.8rem;
  font-weight: 550;
  letter-spacing: 0.04em;
}

.download-count-value {
  color: var(--vp-c-text-1);
  font-size: clamp(1.4rem, 3vw, 1.8rem);
  font-variant-numeric: tabular-nums;
  font-weight: 700;
  letter-spacing: -0.03em;
}
</style>
