<template>
  <a-modal
    v-model:open="cropModalVisible"
    title="裁剪头像"
    :width="900"
    :confirm-loading="avatarLoading"
    @ok="handleCropConfirm"
    @cancel="resetState"
    ok-text="确认"
    cancel-text="取消"
  >
    <div class="crop-container">
      <div class="crop-wrapper">
        <div class="crop-area" :style="cropAreaStyle">
          <img
            ref="imageRef"
            :src="previewUrl"
            @load="onImageLoad"
            alt="preview"
            draggable="false"
          />
          <div
            class="crop-box"
            :style="cropBoxStyle"
            @mousedown.stop.prevent="startCropDrag"
            @touchstart.stop.prevent="startCropDrag"
          >
            <div class="crop-grid"></div>
            <div class="crop-handle nw" @mousedown.stop.prevent="startResize('nw', $event)" @touchstart.stop.prevent="startResize('nw', $event)"></div>
            <div class="crop-handle ne" @mousedown.stop.prevent="startResize('ne', $event)" @touchstart.stop.prevent="startResize('ne', $event)"></div>
            <div class="crop-handle sw" @mousedown.stop.prevent="startResize('sw', $event)" @touchstart.stop.prevent="startResize('sw', $event)"></div>
            <div class="crop-handle se" @mousedown.stop.prevent="startResize('se', $event)" @touchstart.stop.prevent="startResize('se', $event)"></div>
          </div>
        </div>
      </div>
      <div class="crop-preview">
        <h4>预览效果</h4>
        <div class="preview-box">
          <img :src="previewUrl" :style="previewStyle" alt="preview" />
        </div>
        <div class="preview-info">
          <span>裁剪尺寸: {{ Math.round(cropBox.width) }} × {{ Math.round(cropBox.height) }} px</span>
        </div>
        <p class="crop-tips">拖动裁剪框调整位置<br/>拖动四角调整大小</p>
      </div>
    </div>
  </a-modal>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { message } from 'ant-design-vue'
import { uploadAvatar } from '@/api/user'
import { useUserStore } from '@/store/user'
import { useCropBox } from '../composables/useCropBox'

const MAX_FILE_SIZE = 10 * 1024 * 1024
const ALLOWED_TYPES = ['image/jpeg', 'image/png', 'image/gif', 'image/webp', 'image/bmp']

const userStore = useUserStore()

const cropModalVisible = ref(false)
const avatarLoading = ref(false)
const previewUrl = ref('')
const selectedFile = ref<File | null>(null)
const imageRef = ref<HTMLImageElement | null>(null)

const {
  cropBox,
  imageNaturalSize,
  imageDisplaySize,
  cropAreaStyle,
  cropBoxStyle,
  previewStyle,
  onImageLoad,
  startCropDrag,
  startResize
} = useCropBox(imageRef)

const emit = defineEmits<{
  (e: 'success', url: string): void
}>()

/** 供父组件调用：弹出文件选择框 */
function pick(): void {
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = 'image/*'
  input.onchange = (e) => {
    const target = e.target as HTMLInputElement
    if (target.files && target.files[0]) {
      handleFileSelect(target.files[0])
    }
  }
  input.click()
}

defineExpose({ pick })

function handleFileSelect(file: File): void {
  if (!ALLOWED_TYPES.includes(file.type)) {
    message.error('只能上传图片文件（JPEG、PNG、GIF、WebP、BMP）')
    return
  }

  if (file.size > MAX_FILE_SIZE) {
    message.error('图片大小不能超过 10MB')
    return
  }

  selectedFile.value = file
  const reader = new FileReader()
  reader.onload = (e) => {
    previewUrl.value = e.target?.result as string
    cropModalVisible.value = true
  }
  reader.readAsDataURL(file)
}

async function handleCropConfirm(): Promise<void> {
  if (!selectedFile.value || !imageRef.value) return

  avatarLoading.value = true

  try {
    const canvas = document.createElement('canvas')
    const ctx = canvas.getContext('2d')
    if (!ctx) return

    const scaleX = imageNaturalSize.width / imageDisplaySize.width
    const scaleY = imageNaturalSize.height / imageDisplaySize.height

    const cropX = cropBox.x * scaleX
    const cropY = cropBox.y * scaleY
    const cropWidth = cropBox.width * scaleX
    const cropHeight = cropBox.height * scaleY

    canvas.width = cropWidth
    canvas.height = cropHeight

    const img = new Image()
    img.src = previewUrl.value

    await new Promise<void>((resolve) => {
      img.onload = () => {
        ctx.drawImage(img, cropX, cropY, cropWidth, cropHeight, 0, 0, cropWidth, cropHeight)
        resolve()
      }
    })

    canvas.toBlob(async (blob) => {
      if (!blob) return

      const croppedFile = new File([blob], selectedFile.value!.name, { type: selectedFile.value!.type })

      try {
        const url = await uploadAvatar(croppedFile)
        userStore.setUserAvatar(url)
        emit('success', url)
        resetState()
        message.success('头像更新成功')
      } catch (e) {
        console.error(e)
        message.error('头像上传失败')
      } finally {
        avatarLoading.value = false
      }
    }, selectedFile.value.type, 0.9)
  } catch (e) {
    console.error(e)
    avatarLoading.value = false
  }
}

function resetState(): void {
  cropModalVisible.value = false
  previewUrl.value = ''
  selectedFile.value = null
}
</script>

<style lang="scss" scoped>
.crop-container {
  display: flex;
  gap: 24px;
  align-items: flex-start;

  .crop-wrapper {
    flex: 1;
    display: flex;
    justify-content: center;
    align-items: center;
    background: #f0ede4;
    border-radius: 8px;
    padding: 16px;
    min-height: 480px;
  }

  .crop-area {
    position: relative;
    overflow: hidden;
    background: #1a1a1a;
    border-radius: 4px;
    flex-shrink: 0;
    display: flex;
    align-items: center;
    justify-content: center;

    img {
      display: block;
      width: 100%;
      height: 100%;
      object-fit: fill;
      user-select: none;
      -webkit-user-drag: none;
      pointer-events: none;
    }

    .crop-box {
      position: absolute;
      border: 2px solid #fff;
      background: transparent;
      cursor: move;
      box-shadow: 0 0 0 9999px rgba(0, 0, 0, 0.5);
      will-change: transform, left, top, width, height;

      .crop-grid {
        position: absolute;
        top: 0;
        left: 0;
        right: 0;
        bottom: 0;
        pointer-events: none;

        &::before,
        &::after {
          content: '';
          position: absolute;
          left: 0;
          right: 0;
          height: 1px;
          background: rgba(255, 255, 255, 0.4);
        }

        &::before {
          top: 33.33%;
        }

        &::after {
          top: 66.66%;
        }
      }

      .crop-handle {
        position: absolute;
        width: 16px;
        height: 16px;
        background: #fff;
        border: 2px solid $primary-color;
        border-radius: 50%;
        box-shadow: 0 2px 6px rgba(0, 0, 0, 0.3);
        z-index: 10;
        transition: transform 0.1s ease;

        &:hover {
          transform: scale(1.2);
        }

        &.nw {
          top: -8px;
          left: -8px;
          cursor: nw-resize;
        }

        &.ne {
          top: -8px;
          right: -8px;
          cursor: ne-resize;
        }

        &.sw {
          bottom: -8px;
          left: -8px;
          cursor: sw-resize;
        }

        &.se {
          bottom: -8px;
          right: -8px;
          cursor: se-resize;
        }
      }
    }
  }

  .crop-preview {
    flex-shrink: 0;
    text-align: center;
    width: 240px;

    h4 {
      font-size: 14px;
      color: #141413;
      margin-bottom: 16px;
      font-weight: 500;
    }

    .preview-box {
      width: 200px;
      height: 200px;
      border-radius: 50%;
      overflow: hidden;
      border: 3px solid #e0ddd2;
      background: #f0ede4;
      margin: 0 auto;
      box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);

      img {
        max-width: none;
        display: block;
      }
    }

    .preview-info {
      margin-top: 12px;
      font-size: 12px;
      color: #6e6b62;
      background: #f0ede4;
      padding: 6px 12px;
      border-radius: 4px;
    }

    .crop-tips {
      margin-top: 16px;
      font-size: 12px;
      color: #8c8a82;
      line-height: 1.8;
      padding: 12px;
      background: #f5f3ec;
      border-radius: 6px;
    }
  }
}
</style>
