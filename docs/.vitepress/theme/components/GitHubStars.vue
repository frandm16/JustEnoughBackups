<script setup>
import { computed, ref, onMounted } from 'vue'

const stars = ref(null)
const label = computed(() => stars.value === null
  ? 'View Just Enough Backups on GitHub'
  : `Just Enough Backups on GitHub: ${new Intl.NumberFormat().format(stars.value)} stars`)

onMounted(async () => {
  try {
    const res = await fetch('https://api.github.com/repos/frandm16/JustEnoughBackups')
    if (res.ok) {
      const data = await res.json()
      if (typeof data.stargazers_count === 'number') {
        stars.value = data.stargazers_count
      }
    }
  } catch (err) {
    // silent fallback
  }
})
</script>

<template>
  <div class="nav-stars-wrapper">
    <a
      href="https://github.com/frandm16/JustEnoughBackups"
      target="_blank"
      rel="noopener noreferrer"
      class="nav-stars-btn"
      :aria-label="label"
      :title="label"
    >
      <svg class="github-icon" viewBox="0 0 24 24" width="25" height="25" fill="currentColor" aria-hidden="true">
        <path d="M12 .9a11.1 11.1 0 0 0-3.51 21.63c.55.1.76-.24.76-.54v-2.1c-3.1.67-3.76-1.32-3.76-1.32-.5-1.28-1.23-1.62-1.23-1.62-1.01-.69.08-.68.08-.68 1.12.08 1.71 1.15 1.71 1.15 1 1.7 2.62 1.21 3.26.92.1-.72.39-1.21.71-1.49-2.48-.28-5.09-1.24-5.09-5.52 0-1.22.44-2.21 1.15-2.99-.12-.28-.5-1.42.11-2.95 0 0 .94-.3 3.05 1.14a10.6 10.6 0 0 1 5.55 0c2.11-1.44 3.05-1.14 3.05-1.14.61 1.53.23 2.67.11 2.95.72.78 1.15 1.77 1.15 2.99 0 4.29-2.61 5.23-5.1 5.51.4.35.76 1.02.76 2.06v3.09c0 .3.2.65.77.54A11.1 11.1 0 0 0 12 .9Z" />
      </svg>
      <svg class="star-icon" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
        <path d="M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z" />
      </svg>
      <span v-if="stars !== null" class="star-count">{{ new Intl.NumberFormat().format(stars) }}</span>
    </a>
  </div>
</template>

<style scoped>
.nav-stars-wrapper {
  display: inline-flex;
  align-items: center;
}

.nav-stars-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 7px 10px;
  font-size: 15px;
  font-weight: 500;
  line-height: 1;
  border-radius: 6px;
  color: var(--vp-c-text-1);
  text-decoration: none;
  transition: color 0.2s ease;
  user-select: none;
}

.nav-stars-btn:hover {
  color: var(--vp-c-brand-1);
}

.github-icon {
  display: block;
  align-self: center;
  flex-shrink: 0;
}

.star-icon {
  display: block;
  align-self: center;
  color: inherit;
  flex-shrink: 0;
}

.star-count {
  display: inline-flex;
  align-items: center;
  line-height: 1;
  font-weight: 400;
  font-variant-numeric: tabular-nums;
  color: inherit;
}
</style>
