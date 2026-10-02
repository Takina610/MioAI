import { ref, reactive, computed, nextTick, type Ref } from 'vue'

export interface CropBox {
  x: number
  y: number
  width: number
  height: number
}

/** 头像裁剪框：尺寸计算、拖动、四角缩放与预览样式 */
export function useCropBox(imageRef: Ref<HTMLImageElement | null>) {
  const isDragging = ref(false)
  const isResizing = ref(false)
  const resizeDirection = ref('')

  const cropBox = reactive<CropBox>({ x: 50, y: 50, width: 150, height: 150 })
  const imageNaturalSize = reactive({ width: 0, height: 0 })
  const imageDisplaySize = reactive({ width: 0, height: 0 })
  const containerSize = reactive({ width: 500, height: 400 })
  const dragStart = reactive({ x: 0, y: 0 })
  const cropBoxStart = reactive<CropBox>({ x: 0, y: 0, width: 0, height: 0 })

  const cropAreaStyle = computed(() => ({
    width: `${containerSize.width}px`,
    height: `${containerSize.height}px`
  }))

  const cropBoxStyle = computed(() => ({
    left: `${cropBox.x}px`,
    top: `${cropBox.y}px`,
    width: `${cropBox.width}px`,
    height: `${cropBox.height}px`
  }))

  const previewStyle = computed(() => {
    const previewSize = 200
    const scaleX = previewSize / cropBox.width
    const scaleY = previewSize / cropBox.height
    return {
      transform: `translate(${-cropBox.x * scaleX}px, ${-cropBox.y * scaleY}px) scale(${scaleX})`,
      transformOrigin: '0 0',
      width: `${imageDisplaySize.width}px`,
      height: `${imageDisplaySize.height}px`
    }
  })

  /** 图片加载后按比例计算容器尺寸并居中裁剪框 */
  function onImageLoad(): void {
    if (!imageRef.value) return
    imageNaturalSize.width = imageRef.value.naturalWidth
    imageNaturalSize.height = imageRef.value.naturalHeight

    const maxWidth = 550
    const maxHeight = 450
    const imgRatio = imageNaturalSize.width / imageNaturalSize.height

    if (imgRatio > 1) {
      if (imageNaturalSize.width > maxWidth) {
        containerSize.width = maxWidth
        containerSize.height = maxWidth / imgRatio
      } else {
        containerSize.width = imageNaturalSize.width
        containerSize.height = imageNaturalSize.height
      }
    } else {
      if (imageNaturalSize.height > maxHeight) {
        containerSize.height = maxHeight
        containerSize.width = maxHeight * imgRatio
      } else {
        containerSize.width = imageNaturalSize.width
        containerSize.height = imageNaturalSize.height
      }
    }

    imageDisplaySize.width = containerSize.width
    imageDisplaySize.height = containerSize.height

    nextTick(() => {
      const minSize = Math.min(imageDisplaySize.width, imageDisplaySize.height, 180)
      cropBox.width = minSize
      cropBox.height = minSize
      cropBox.x = (imageDisplaySize.width - minSize) / 2
      cropBox.y = (imageDisplaySize.height - minSize) / 2
    })
  }

  function getEventPoint(e: MouseEvent | TouchEvent): { clientX: number; clientY: number } {
    return 'touches' in e
      ? { clientX: e.touches[0].clientX, clientY: e.touches[0].clientY }
      : { clientX: e.clientX, clientY: e.clientY }
  }

  let animationFrameId: number | null = null
  let pendingUpdate: { x: number; y: number } | null = null

  function startCropDrag(e: MouseEvent | TouchEvent): void {
    if (isResizing.value) return
    isDragging.value = true

    const { clientX, clientY } = getEventPoint(e)
    dragStart.x = clientX
    dragStart.y = clientY
    cropBoxStart.x = cropBox.x
    cropBoxStart.y = cropBox.y

    document.addEventListener('mousemove', onDrag, { passive: true })
    document.addEventListener('mouseup', stopDrag)
    document.addEventListener('touchmove', onDrag, { passive: true })
    document.addEventListener('touchend', stopDrag)
  }

  function onDrag(e: MouseEvent | TouchEvent): void {
    if (!isDragging.value) return

    const { clientX, clientY } = getEventPoint(e)
    const newX = cropBoxStart.x + clientX - dragStart.x
    const newY = cropBoxStart.y + clientY - dragStart.y

    pendingUpdate = {
      x: Math.max(0, Math.min(imageDisplaySize.width - cropBox.width, newX)),
      y: Math.max(0, Math.min(imageDisplaySize.height - cropBox.height, newY))
    }

    if (!animationFrameId) {
      animationFrameId = requestAnimationFrame(applyPendingPosition)
    }
  }

  function applyPendingPosition(): void {
    if (pendingUpdate) {
      cropBox.x = pendingUpdate.x
      cropBox.y = pendingUpdate.y
      pendingUpdate = null
    }
    animationFrameId = null
  }

  function stopDrag(): void {
    isDragging.value = false
    if (animationFrameId) {
      cancelAnimationFrame(animationFrameId)
      animationFrameId = null
    }
    applyPendingPosition()
    document.removeEventListener('mousemove', onDrag)
    document.removeEventListener('mouseup', stopDrag)
    document.removeEventListener('touchmove', onDrag)
    document.removeEventListener('touchend', stopDrag)
  }

  let resizeAnimationFrameId: number | null = null
  let pendingResizeUpdate: { x: number; y: number; size: number } | null = null

  function startResize(direction: string, e: MouseEvent | TouchEvent): void {
    isResizing.value = true
    resizeDirection.value = direction

    const { clientX, clientY } = getEventPoint(e)
    dragStart.x = clientX
    dragStart.y = clientY
    cropBoxStart.x = cropBox.x
    cropBoxStart.y = cropBox.y
    cropBoxStart.width = cropBox.width
    cropBoxStart.height = cropBox.height

    document.addEventListener('mousemove', onResize, { passive: true })
    document.addEventListener('mouseup', stopResize)
    document.addEventListener('touchmove', onResize, { passive: true })
    document.addEventListener('touchend', stopResize)
  }

  function onResize(e: MouseEvent | TouchEvent): void {
    if (!isResizing.value) return

    const { clientX, clientY } = getEventPoint(e)
    const deltaX = clientX - dragStart.x
    const deltaY = clientY - dragStart.y

    const minSize = 50
    const maxSize = Math.min(imageDisplaySize.width, imageDisplaySize.height)

    let newSize = cropBoxStart.width
    let newX = cropBoxStart.x
    let newY = cropBoxStart.y

    switch (resizeDirection.value) {
      case 'se':
        newSize = Math.max(minSize, Math.min(maxSize, cropBoxStart.width + Math.max(deltaX, deltaY)))
        break
      case 'sw':
        newSize = Math.max(minSize, Math.min(maxSize, cropBoxStart.width - Math.min(deltaX, deltaY)))
        newX = cropBoxStart.x + cropBoxStart.width - newSize
        break
      case 'ne':
        newSize = Math.max(minSize, Math.min(maxSize, cropBoxStart.width + Math.max(deltaX, -deltaY)))
        newY = cropBoxStart.y + cropBoxStart.height - newSize
        break
      case 'nw':
        newSize = Math.max(minSize, Math.min(maxSize, cropBoxStart.width - Math.max(deltaX, deltaY)))
        newX = cropBoxStart.x + cropBoxStart.width - newSize
        newY = cropBoxStart.y + cropBoxStart.height - newSize
        break
    }

    if (newX >= 0 && newY >= 0 && newX + newSize <= imageDisplaySize.width && newY + newSize <= imageDisplaySize.height) {
      pendingResizeUpdate = { x: newX, y: newY, size: newSize }

      if (!resizeAnimationFrameId) {
        resizeAnimationFrameId = requestAnimationFrame(applyPendingResize)
      }
    }
  }

  function applyPendingResize(): void {
    if (pendingResizeUpdate) {
      cropBox.x = pendingResizeUpdate.x
      cropBox.y = pendingResizeUpdate.y
      cropBox.width = pendingResizeUpdate.size
      cropBox.height = pendingResizeUpdate.size
      pendingResizeUpdate = null
    }
    resizeAnimationFrameId = null
  }

  function stopResize(): void {
    isResizing.value = false
    if (resizeAnimationFrameId) {
      cancelAnimationFrame(resizeAnimationFrameId)
      resizeAnimationFrameId = null
    }
    applyPendingResize()
    document.removeEventListener('mousemove', onResize)
    document.removeEventListener('mouseup', stopResize)
    document.removeEventListener('touchmove', onResize)
    document.removeEventListener('touchend', stopResize)
  }

  return {
    cropBox,
    imageNaturalSize,
    imageDisplaySize,
    cropAreaStyle,
    cropBoxStyle,
    previewStyle,
    onImageLoad,
    startCropDrag,
    startResize
  }
}
