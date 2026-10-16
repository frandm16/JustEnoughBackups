<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'

const image = ref<{ src: string; alt: string } | null>(null)

function openImage(event: MouseEvent) {
  const target = event.target
  if (!(target instanceof HTMLImageElement) || !target.closest('.vp-doc')) return

  event.preventDefault()
  image.value = { src: target.currentSrc || target.src, alt: target.alt }
  document.body.style.overflow = 'hidden'
}

function closeImage() {
  image.value = null
  document.body.style.overflow = ''
}

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape' && image.value) closeImage()
}

onMounted(() => {
  document.addEventListener('click', openImage)
  document.addEventListener('keydown', onKeydown)
})

onUnmounted(() => {
  document.removeEventListener('click', openImage)
  document.removeEventListener('keydown', onKeydown)
  document.body.style.overflow = ''
})
</script>

<template>
  <Teleport to="body">
    <div
      v-if="image"
      class="doc-image-lightbox"
      role="dialog"
      aria-modal="true"
      :aria-label="image.alt || 'Expanded image'"
      @click.self="closeImage"
    >
      <button class="doc-image-lightbox-close" type="button" aria-label="Close image" @click="closeImage">
        <span aria-hidden="true">×</span>
      </button>
      <img :src="image.src" :alt="image.alt" />
    </div>
  </Teleport>
</template>
