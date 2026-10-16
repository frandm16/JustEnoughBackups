<script setup>
import { ref, onMounted, onUnmounted } from 'vue'

const open = ref(false)
const containerRef = ref(null)

const toggle = (e) => {
  e.stopPropagation()
  open.value = !open.value
}

const close = () => {
  open.value = false
}

const onDocClick = (e) => {
  if (containerRef.value && !containerRef.value.contains(e.target)) {
    close()
  }
}

onMounted(() => {
  if (typeof document !== 'undefined') {
    document.addEventListener('click', onDocClick)
  }
})

onUnmounted(() => {
  if (typeof document !== 'undefined') {
    document.removeEventListener('click', onDocClick)
  }
})
</script>

<template>
  <div
    ref="containerRef"
    class="nav-download-wrapper"
    @keydown.esc.stop.prevent="close"
  >
    <button
      class="nav-download-btn"
      type="button"
      aria-label="Download Just Enough Backups"
      aria-haspopup="true"
      :aria-expanded="open"
      @click="toggle"
    >
      <span>Download</span>
      <svg class="chevron-icon" :class="{ rotated: open }" viewBox="0 0 20 20" width="13" height="13" fill="currentColor">
        <path fill-rule="evenodd" d="M5.293 7.293a1 1 0 011.414 0L10 10.586l3.293-3.293a1 1 0 111.414 1.414l-4 4a1 1 0 01-1.414 0l-4-4a1 1 0 010-1.414z" clip-rule="evenodd" />
      </svg>
    </button>

    <!-- Invisible hover bridge prevents closing when moving mouse into dropdown -->
    <div v-show="open" class="nav-download-bridge">
      <div class="nav-download-menu">
        <a
          href="https://modrinth.com/mod/justenoughbackups-jeb"
          target="_blank"
          rel="noopener noreferrer"
          class="menu-item"
          @click="close"
        >
          <span class="item-title">Modrinth</span>
        </a>
        <a
          href="https://www.curseforge.com/minecraft/mc-mods/just-enough-backups"
          target="_blank"
          rel="noopener noreferrer"
          class="menu-item"
          @click="close"
        >
          <span class="item-title">CurseForge</span>
        </a>
      </div>
    </div>
  </div>
</template>

<style scoped>
.nav-download-wrapper {
  position: relative;
  display: inline-flex;
  align-items: center;
}

.nav-download-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 5px 14px;
  font-size: 13px;
  font-weight: 600;
  color: var(--jeb-action-text) !important;
  background: var(--vp-c-brand-1);
  border-radius: 9999px;
  border: none;
  cursor: pointer;
  transition: all 0.2s ease;
  user-select: none;
  box-shadow: none;
}

.nav-download-btn:hover {
  background: var(--jeb-action-hover);
  box-shadow: 0 3px 10px rgba(41, 74, 99, 0.18);
}

.chevron-icon {
  transition: transform 0.2s ease;
}

.chevron-icon.rotated {
  transform: rotate(180deg);
}

/* Zero gap bridge between button and popup */
.nav-download-bridge {
  position: absolute;
  top: 100%;
  right: 0;
  padding-top: 6px;
  z-index: 9999;
}

.nav-download-menu {
  min-width: 140px;
  background: var(--vp-c-bg-elv);
  border: 1px solid var(--vp-c-divider);
  border-radius: 8px;
  box-shadow: var(--vp-shadow-3);
  padding: 4px;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.menu-item {
  display: flex;
  align-items: center;
  padding: 8px 12px;
  border-radius: 6px;
  text-decoration: none;
  color: var(--vp-c-text-1) !important;
  transition: background 0.15s ease, color 0.15s ease;
}

.menu-item:hover {
  background: var(--vp-c-brand-soft);
  color: var(--vp-c-brand-1) !important;
}

.item-title {
  font-size: 13px;
  font-weight: 600;
}
</style>
