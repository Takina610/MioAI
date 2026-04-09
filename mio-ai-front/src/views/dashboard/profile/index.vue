<template>
  <div class="profile-page">
    <div class="page-header">
      <h2>个人中心</h2>
      <p class="desc">管理您的个人信息</p>
      <div class="header-line"></div>
    </div>

    <div class="profile-content">
      <a-row :gutter="24">
        <a-col :span="6">
          <a-card class="avatar-card" :bordered="false">
            <div class="avatar-section">
              <div class="avatar-preview" @click="triggerFileSelect">
                <a-avatar :size="100" :src="previewUrl || userStore.userAvatar">
                  {{ userStore.userName?.charAt(0)?.toUpperCase() }}
                </a-avatar>
                <div class="avatar-overlay">
                  <EditOutlined />
                  <span>更换头像</span>
                </div>
              </div>
              <div class="user-basic">
                <div class="user-name">{{ userStore.userName }}</div>
                <div class="user-role">{{ userStore.userInfo?.userRole === 'admin' ? '管理员' : '普通用户' }}</div>
              </div>
            </div>
          </a-card>
        </a-col>

        <a-col :span="18">
          <a-card class="info-card" :bordered="false">
            <template #title>
              <span>基本信息</span>
            </template>
            <a-form
              ref="formRef"
              :model="formData"
              :rules="rules"
              @finish="handleUpdate"
            >
              <div class="form-grid">
                <div class="form-item">
                  <span class="label">昵称</span>
                  <a-input :value="formData.userName" @update:value="formData.userName = $event" placeholder="请输入昵称" />
                </div>
                <div class="form-item">
                  <span class="label">账号</span>
                  <a-input :value="formData.userAccount" @update:value="formData.userAccount = $event" disabled />
                </div>
                <div class="form-item">
                  <span class="label">角色</span>
                  <a-input :value="formData.userRole === 'admin' ? '管理员' : '普通用户'" disabled />
                </div>
                <div class="form-item">
                  <span class="label">简介</span>
                  <a-input :value="formData.userProfile" @update:value="formData.userProfile = $event" placeholder="请输入个人简介" />
                </div>
              </div>
              <div class="form-actions">
                <a-button type="primary" html-type="submit" :loading="loading">
                  保存修改
                </a-button>
              </div>
            </a-form>
          </a-card>
        </a-col>
      </a-row>

      <a-card class="security-card" :bordered="false">
        <template #title>
          <span>安全设置</span>
        </template>
        <div class="security-item">
          <div class="item-info">
            <span class="label">登录密码</span>
            <span class="desc">定期更换密码可以提高账号安全性</span>
          </div>
          <a-button @click="showPasswordModal">修改密码</a-button>
        </div>
      </a-card>
    </div>

    <a-modal
      :open="passwordModalVisible"
      @update:open="passwordModalVisible = $event"
      title="修改密码"
      :confirm-loading="passwordLoading"
      @ok="handlePasswordChange"
      ok-text="确认"
      cancel-text="取消"
    >
      <a-form
        ref="passwordFormRef"
        :model="passwordForm"
        :rules="passwordRules"
        layout="vertical"
      >
        <a-form-item name="oldPassword" label="原密码">
          <a-input-password :value="passwordForm.oldPassword" @update:value="passwordForm.oldPassword = $event" placeholder="请输入原密码" />
        </a-form-item>
        <a-form-item name="newPassword" label="新密码">
          <a-input-password :value="passwordForm.newPassword" @update:value="passwordForm.newPassword = $event" placeholder="请输入新密码" />
        </a-form-item>
        <a-form-item name="confirmPassword" label="确认密码">
          <a-input-password :value="passwordForm.confirmPassword" @update:value="passwordForm.confirmPassword = $event" placeholder="请再次输入新密码" />
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal
      v-model:open="cropModalVisible"
      title="裁剪头像"
      :width="900"
      :confirm-loading="avatarLoading"
      @ok="handleCropConfirm"
      @cancel="handleCropCancel"
      ok-text="确认"
      cancel-text="取消"
    >
      <div class="crop-container">
        <div class="crop-wrapper">
          <div class="crop-area" ref="cropAreaRef" :style="cropAreaStyle">
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
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed, nextTick } from 'vue'
import { message, type FormInstance, type UploadProps } from 'ant-design-vue'
import type { Rule } from 'ant-design-vue/es/form'
import { useUserStore } from '@/store/user'
import { updateUser, updatePassword, uploadAvatar } from '@/api/user'
import type { UpdateUserRequest, PasswordUpdateRequest, UserVO } from '@/types'
import { EditOutlined } from '@ant-design/icons-vue'

interface FormData {
  id: number | null
  userName: string
  userAccount: string
  userRole: string
  userProfile: string
}

interface CropBox {
  x: number
  y: number
  width: number
  height: number
}

const userStore = useUserStore()
const formRef = ref<FormInstance | null>(null)
const passwordFormRef = ref<FormInstance | null>(null)
const loading = ref<boolean>(false)
const passwordLoading = ref<boolean>(false)
const avatarLoading = ref<boolean>(false)
const passwordModalVisible = ref<boolean>(false)
const cropModalVisible = ref<boolean>(false)

const previewUrl = ref<string>('')
const selectedFile = ref<File | null>(null)
const imageRef = ref<HTMLImageElement | null>(null)

const cropBox = reactive<CropBox>({
  x: 50,
  y: 50,
  width: 150,
  height: 150
})

const imageNaturalSize = reactive({ width: 0, height: 0 })
const imageDisplaySize = reactive({ width: 0, height: 0 })
const containerSize = reactive({ width: 500, height: 400 })
const isDragging = ref<boolean>(false)
const isResizing = ref<boolean>(false)
const resizeDirection = ref<string>('')
const dragStart = reactive({ x: 0, y: 0 })
const cropBoxStart = reactive<CropBox>({ x: 0, y: 0, width: 0, height: 0 })

const formData = reactive<FormData>({
  id: null,
  userName: '',
  userAccount: '',
  userRole: '',
  userProfile: ''
})

const passwordForm = reactive<PasswordUpdateRequest>({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const rules: Record<string, Rule[]> = {
  userName: [{ required: true, message: '请输入昵称', trigger: 'blur' }]
}

const validateConfirmPassword = async (_rule: Rule, value: string): Promise<void> => {
  if (!value) {
    return Promise.reject('请确认密码')
  }
  if (value !== passwordForm.newPassword) {
    return Promise.reject('两次输入的密码不一致')
  }
  return Promise.resolve()
}

const passwordRules: Record<string, Rule[]> = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 8, max: 20, message: '密码长度为8-20个字符', trigger: 'blur' }
  ],
  confirmPassword: [{ validator: validateConfirmPassword, trigger: 'blur' }]
}

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

onMounted(() => {
  if (userStore.userInfo) {
    const info: UserVO = userStore.userInfo
    Object.assign(formData, {
      id: info.id,
      userName: info.userName,
      userAccount: info.userAccount,
      userRole: info.userRole,
      userProfile: info.userProfile || ''
    })
  }
})

const beforeUpload: UploadProps['beforeUpload'] = (file) => {
  const isImage = file.type.startsWith('image/')
  if (!isImage) {
    message.error('只能上传图片文件')
    return false
  }
  const isLt20M = file.size / 1024 / 1024 < 20
  if (!isLt20M) {
    message.error('图片大小不能超过 20MB')
    return false
  }
  return true
}

function triggerFileSelect(): void {
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = 'image/*'
  input.onchange = (e) => {
    const target = e.target as HTMLInputElement
    if (target.files && target.files[0]) {
      handleFileSelect({ file: target.files[0] } as { file: File })
    }
  }
  input.click()
}

function handleFileSelect(options: { file: File }): void {
  selectedFile.value = options.file
  const reader = new FileReader()
  reader.onload = (e) => {
    previewUrl.value = e.target?.result as string
    cropModalVisible.value = true
    resetCropBox()
  }
  reader.readAsDataURL(options.file)
}

function resetCropBox(): void {
  cropBox.x = 50
  cropBox.y = 50
  cropBox.width = 150
  cropBox.height = 150
}

function onImageLoad(): void {
  if (imageRef.value) {
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
}

let animationFrameId: number | null = null
let pendingUpdate: { x: number; y: number } | null = null

function startCropDrag(e: MouseEvent | TouchEvent): void {
  if (isResizing.value) return
  isDragging.value = true
  
  const clientX = 'touches' in e ? e.touches[0].clientX : e.clientX
  const clientY = 'touches' in e ? e.touches[0].clientY : e.clientY
  
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
  
  const clientX = 'touches' in e ? e.touches[0].clientX : e.clientX
  const clientY = 'touches' in e ? e.touches[0].clientY : e.clientY
  
  const deltaX = clientX - dragStart.x
  const deltaY = clientY - dragStart.y
  
  const newX = cropBoxStart.x + deltaX
  const newY = cropBoxStart.y + deltaY
  
  pendingUpdate = {
    x: Math.max(0, Math.min(imageDisplaySize.width - cropBox.width, newX)),
    y: Math.max(0, Math.min(imageDisplaySize.height - cropBox.height, newY))
  }
  
  if (!animationFrameId) {
    animationFrameId = requestAnimationFrame(updateCropBoxPosition)
  }
}

function updateCropBoxPosition(): void {
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
  if (pendingUpdate) {
    cropBox.x = pendingUpdate.x
    cropBox.y = pendingUpdate.y
    pendingUpdate = null
  }
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
  
  const clientX = 'touches' in e ? e.touches[0].clientX : e.clientX
  const clientY = 'touches' in e ? e.touches[0].clientY : e.clientY
  
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
  
  const clientX = 'touches' in e ? e.touches[0].clientX : e.clientX
  const clientY = 'touches' in e ? e.touches[0].clientY : e.clientY
  
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
      resizeAnimationFrameId = requestAnimationFrame(updateResizeBox)
    }
  }
}

function updateResizeBox(): void {
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
  if (pendingResizeUpdate) {
    cropBox.x = pendingResizeUpdate.x
    cropBox.y = pendingResizeUpdate.y
    cropBox.width = pendingResizeUpdate.size
    cropBox.height = pendingResizeUpdate.size
    pendingResizeUpdate = null
  }
  document.removeEventListener('mousemove', onResize)
  document.removeEventListener('mouseup', stopResize)
  document.removeEventListener('touchmove', onResize)
  document.removeEventListener('touchend', stopResize)
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
        previewUrl.value = ''
        cropModalVisible.value = false
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

function handleCropCancel(): void {
  cropModalVisible.value = false
  previewUrl.value = ''
  selectedFile.value = null
}

async function handleUpdate(): Promise<void> {
  loading.value = true
  try {
    await updateUser(formData as UpdateUserRequest)
    message.success('更新成功')
    if (userStore.userInfo) {
      userStore.setUserInfo({ ...userStore.userInfo, ...formData } as UserVO)
    }
  } catch (e) {
    console.error(e)
  } finally {
    loading.value = false
  }
}

function showPasswordModal(): void {
  passwordModalVisible.value = true
  passwordFormRef.value?.resetFields()
}

async function handlePasswordChange(): Promise<void> {
  try {
    await passwordFormRef.value?.validate()
    passwordLoading.value = true
    await updatePassword(passwordForm)
    message.success('密码修改成功，请重新登录')
    passwordModalVisible.value = false
    await userStore.logout()
    window.location.href = '/'
  } catch (e) {
    console.error(e)
  } finally {
    passwordLoading.value = false
  }
}
</script>

<style lang="scss" scoped>
.profile-page {
  max-width: 1000px;
  margin: 0 auto;
  height: 100%;
  display: flex;
  flex-direction: column;

  .page-header {
    margin-top: 20px;
    margin-bottom: 20px;
    flex-shrink: 0;

    h2 {
      font-size: 24px;
      font-weight: 600;
      color: #202124;
      margin-bottom: 4px;
    }

    .desc {
      color: #5f6368;
      font-size: 14px;
      margin-bottom: 12px;
    }

    .header-line {
      height: 1px;
      background: #e8eaed;
    }
  }

  .profile-content {
    flex: 1;
    display: flex;
    flex-direction: column;
    gap: 20px;

    .avatar-card,
    .info-card,
    .security-card {
      border-radius: 12px;
      background: #fff;
      box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);

      :deep(.ant-card-head) {
        min-height: 48px;
        padding: 0 20px;
        border-bottom: 1px solid #f0f0f0;

        .ant-card-head-title {
          font-weight: 600;
          font-size: 16px;
          padding: 12px 0;
        }
      }

      :deep(.ant-card-body) {
        padding: 20px;
      }
    }

    .avatar-card {
      height: 100%;

      .avatar-section {
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        gap: 16px;

        .avatar-preview {
          position: relative;
          cursor: pointer;
          border-radius: 50%;
          overflow: hidden;

          .avatar-overlay {
            position: absolute;
            top: 0;
            left: 0;
            right: 0;
            bottom: 0;
            background: rgba(0, 0, 0, 0.5);
            display: flex;
            flex-direction: column;
            align-items: center;
            justify-content: center;
            color: white;
            opacity: 0;
            transition: opacity 0.3s;

            .anticon {
              font-size: 20px;
              margin-bottom: 4px;
            }

            span {
              font-size: 12px;
            }
          }

          &:hover .avatar-overlay {
            opacity: 1;
          }
        }

        .user-basic {
          text-align: center;

          .user-name {
            font-size: 16px;
            font-weight: 600;
            color: #202124;
            margin-bottom: 4px;
          }

          .user-role {
            font-size: 13px;
            color: #666;
          }
        }
      }
    }

    .info-card {
      .form-grid {
        width: 100%;
        display: grid;
        grid-template-columns: repeat(2, 1fr);
        gap: 20px;

        .form-item {
          display: flex;
          align-items: center;
          gap: 12px;

          .label {
            font-size: 14px;
            color: #666;
            min-width: 50px;
            flex-shrink: 0;
          }

          :deep(.ant-input) {
            flex: 1;
          }
        }
      }

      .form-actions {
        width: 100%;
        display: block;
        margin-top: 20px;
        padding-top: 16px;
        border-top: 1px solid #f0f0f0;
        clear: both;
      }
    }

    .security-card {
      flex-shrink: 0;

      .security-item {
        display: flex;
        justify-content: space-between;
        align-items: center;

        .item-info {
          display: flex;
          align-items: center;
          gap: 16px;

          .label {
            font-size: 15px;
            font-weight: 500;
            color: $text-dark;
          }

          .desc {
            font-size: 13px;
            color: #999;
          }
        }
      }
    }

    :deep(.ant-btn-primary) {
      background: $primary-color;
      border-color: $primary-color;

      &:hover {
        background: #238b92;
        border-color: #238b92;
      }
    }
  }
}

.crop-container {
  display: flex;
  gap: 24px;
  align-items: flex-start;

  .crop-wrapper {
    flex: 1;
    display: flex;
    justify-content: center;
    align-items: center;
    background: #f5f5f5;
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
      
      @mixin crop-handle-base {
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
      }

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
        @include crop-handle-base;

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
      color: #333;
      margin-bottom: 16px;
      font-weight: 500;
    }

    .preview-box {
      width: 200px;
      height: 200px;
      border-radius: 50%;
      overflow: hidden;
      border: 3px solid #e8e8e8;
      background: #f5f5f5;
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
      color: #666;
      background: #f5f5f5;
      padding: 6px 12px;
      border-radius: 4px;
    }

    .crop-tips {
      margin-top: 16px;
      font-size: 12px;
      color: #999;
      line-height: 1.8;
      padding: 12px;
      background: #fafafa;
      border-radius: 6px;
    }
  }
}
</style>
