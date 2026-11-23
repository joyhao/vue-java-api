<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import request from '@/api/request'

const props = defineProps<{ src: string; alt: string }>()
const imageUrl = ref('')
let version = 0
let controller: AbortController | undefined

function release() {
  if (imageUrl.value) URL.revokeObjectURL(imageUrl.value)
  imageUrl.value = ''
}

watch(() => props.src, async (src) => {
  const current = ++version
  controller?.abort()
  controller = new AbortController()
  release()
  if (!src) return
  try {
    const response = await request.get<Blob>(src, { responseType: 'blob', signal: controller.signal })
    if (current === version) imageUrl.value = URL.createObjectURL(response.data)
  } catch {
    if (current === version) release()
  }
}, { immediate: true })

onBeforeUnmount(() => {
  version++
  controller?.abort()
  release()
})
</script>

<template>
  <img v-if="imageUrl" :src="imageUrl" :alt="alt" />
  <img v-else :alt="alt" />
</template>
