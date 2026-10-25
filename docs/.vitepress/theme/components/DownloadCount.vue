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
  <div class="download-count" aria-live="polite" aria-label="Total downloads from Modrinth and CurseForge">
    <div class="download-count-main">
      <strong v-if="total !== null" class="download-count-value">
        {{ total >= 1_000_000 ? `${(total / 1_000_000).toFixed(1)}M` : `${(total / 1_000).toFixed(1)}K` }}
      </strong>
      <span v-else-if="unavailable" class="download-count-value">Unavailable</span>
      <span v-else class="download-count-value">Loading…</span>
      <span v-if="total !== null" class="download-count-title">Downloads</span>
    </div>
    <span class="download-count-label">Modrinth + CurseForge</span>
  </div>
</template>

<style scoped>
.download-count {
  display: inline-flex;
  flex-direction: column;
  align-items: center;
  gap: 0.2rem;
  margin: 1.5rem auto 0;
  padding: 0.9rem 1.5rem;
  border: 1px solid color-mix(in srgb, var(--vp-c-brand-1) 30%, var(--vp-c-divider));
  border-radius: 16px;
  background: linear-gradient(145deg, var(--vp-c-bg-soft), var(--vp-c-bg));
  box-shadow: 0 8px 24px rgba(27, 43, 56, 0.08);
  color: var(--vp-c-text-2);
  text-align: center;
}

.download-count-main {
  display: flex;
  align-items: baseline;
  justify-content: center;
  gap: 0.5rem;
  color: var(--vp-c-text-1);
}

.download-count-value {
  color: var(--vp-c-brand-1);
  font-size: clamp(1.65rem, 4vw, 2.1rem);
  font-variant-numeric: tabular-nums;
  font-weight: 750;
  letter-spacing: -0.04em;
}

.download-count-title {
  font-size: 1rem;
  font-weight: 650;
  letter-spacing: -0.02em;
}

.download-count-label {
  font-size: 0.72rem;
  font-weight: 550;
  letter-spacing: 0.07em;
  text-transform: uppercase;
}
</style>
