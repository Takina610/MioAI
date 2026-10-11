<template>
  <div class="dash-page">
    <PageHeader back-label="智能体管理" back-to="/dashboard/agent" title="创建智能体" />

    <div class="dash-page-content">
      <LoginPrompt v-if="!userStore.isLoggedIn" @login="emit('login-required')" />

      <div v-else class="creator">
        <!-- 顶部：实时预览 -->
        <div class="creator-preview">
          <BotIcon
            ref="previewRef"
            :shape="config.shape"
            :fill="config.fill"
            :state="previewState"
            :size="150"
            follow
            eye-color="#f0eee6"
          />
        </div>

        <!-- 颜色 -->
        <section class="creator-section">
          <h3 class="section-title">颜色</h3>
          <div class="swatch-row">
            <button
              v-for="p in BOT_PALETTE"
              :key="p.id"
              type="button"
              class="swatch"
              :class="{ active: isActivePalette(p.id) }"
              :style="{ background: p.color }"
              :aria-label="p.id"
              @click="selectPalette(p.id)"
            />
            <div class="swatch custom" :class="{ active: config.fill.kind === 'solid' }">
              <input
                type="color"
                :value="customColor"
                aria-label="自定义颜色"
                @input="selectCustom(($event.target as HTMLInputElement).value)"
              />
            </div>
          </div>
          <div class="swatch-row gradient-row">
            <button
              type="button"
              class="swatch gradient linear"
              :class="{ active: config.fill.kind === 'linear' }"
              :style="gradientSwatchStyle"
              aria-label="线性渐变"
              @click="selectGradient('linear')"
            />
            <button
              type="button"
              class="swatch gradient radial"
              :class="{ active: config.fill.kind === 'radial' }"
              :style="gradientSwatchStyle"
              aria-label="径向渐变"
              @click="selectGradient('radial')"
            />
            <template v-if="config.fill.kind === 'linear' || config.fill.kind === 'radial'">
              <label class="grad-picker">
                <input type="color" :value="gradFrom" @input="setGradColor('from', ($event.target as HTMLInputElement).value)" />
                <span>起点</span>
              </label>
              <label class="grad-picker">
                <input type="color" :value="gradTo" @input="setGradColor('to', ($event.target as HTMLInputElement).value)" />
                <span>终点</span>
              </label>
              <label v-if="config.fill.kind === 'linear'" class="grad-angle">
                <input
                  type="range"
                  min="0"
                  max="360"
                  :value="gradAngle"
                  @input="setGradAngle(Number(($event.target as HTMLInputElement).value))"
                />
                <span>{{ gradAngle }}°</span>
              </label>
            </template>
          </div>
        </section>

        <!-- 形体 -->
        <section class="creator-section">
          <h3 class="section-title">形体</h3>
          <div class="shape-grid">
            <button
              v-for="s in BOT_SHAPES"
              :key="s.id"
              type="button"
              class="shape-item"
              :class="{ active: config.shape === s.id }"
              @click="config.shape = s.id"
            >
              <BotIcon :shape="s.id" :fill="config.fill" :size="46" :live="false" eye-color="#f0eee6" />
              <span class="shape-label">{{ s.label }}</span>
            </button>
          </div>
        </section>

        <!-- 名称 / 描述 -->
        <section class="creator-section">
          <h3 class="section-title">名称</h3>
          <a-input v-model:value="name" :maxlength="100" placeholder="给智能体起个名字" />
        </section>

        <section class="creator-section">
          <h3 class="section-title">描述</h3>
          <a-textarea v-model:value="description" :maxlength="500" :rows="3" placeholder="它擅长什么、用于什么场景" />
        </section>

        <!-- 底部：创建 -->
        <div class="creator-footer">
          <a-button
            type="primary"
            size="large"
            class="start-btn"
            :disabled="!name.trim() || submitting"
            :loading="submitting"
            @click="handleCreate"
          >
            立即开始
          </a-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { addAgent } from '@/api/agent'
import { useUserStore } from '@/store/user'
import PageHeader from '../components/PageHeader.vue'
import LoginPrompt from '../components/LoginPrompt.vue'
import BotIcon from '@/components/bot-icon/BotIcon.vue'
import { BOT_PALETTE, BOT_SHAPES, serializeBotIcon } from '@/components/bot-icon/types'
import type { BotFill, BotIconConfig, BotPaletteId } from '@/components/bot-icon/types'

const emit = defineEmits<{ (e: 'login-required'): void }>()

const router = useRouter()
const userStore = useUserStore()

const config = ref<BotIconConfig>({
  shape: 'blob',
  fill: { kind: 'palette', id: 'black' }
})
const name = ref('')
const description = ref('')
const submitting = ref(false)
const previewState = ref('idle')
const previewRef = ref<InstanceType<typeof BotIcon> | null>(null)

const customColor = ref('#2aa1a9')
const gradFrom = ref('#2aa1a9')
const gradTo = ref('#0e74e0')
const gradAngle = ref(135)

const gradientSwatchStyle = computed(() => ({
  background: `linear-gradient(135deg, ${gradFrom.value}, ${gradTo.value})`
}))

function isActivePalette(id: BotPaletteId): boolean {
  return config.value.fill.kind === 'palette' && config.value.fill.id === id
}

function selectPalette(id: BotPaletteId): void {
  config.value.fill = { kind: 'palette', id }
}

function selectCustom(color: string): void {
  customColor.value = color
  config.value.fill = { kind: 'solid', color }
}

function selectGradient(kind: 'linear' | 'radial'): void {
  if (kind === 'linear') {
    config.value.fill = { kind: 'linear', from: gradFrom.value, to: gradTo.value, angle: gradAngle.value }
  } else {
    config.value.fill = { kind: 'radial', from: gradFrom.value, to: gradTo.value }
  }
}

function setGradColor(part: 'from' | 'to', color: string): void {
  if (part === 'from') gradFrom.value = color
  else gradTo.value = color
  selectGradient(config.value.fill.kind === 'radial' ? 'radial' : 'linear')
}

function setGradAngle(angle: number): void {
  gradAngle.value = angle
  if (config.value.fill.kind === 'linear') {
    config.value.fill = { ...config.value.fill, angle }
  }
}

async function handleCreate(): Promise<void> {
  if (!name.value.trim() || submitting.value) return
  submitting.value = true
  try {
    const agentId = await addAgent({
      name: name.value.trim(),
      description: description.value.trim() || undefined,
      icon: serializeBotIcon(config.value)
    })
    // 创建成功：转一圈撒花再进编辑页
    previewState.value = 'celebrate'
    previewRef.value?.spinOnce(2)
    previewRef.value?.burstOnce()
    message.success('创建成功')
    window.setTimeout(() => {
      router.push(`/dashboard/agent/${agentId}`)
    }, 1000)
  } catch (e) {
    console.error(e)
    message.error('创建失败')
    submitting.value = false
  }
}
</script>

<style lang="scss" scoped>
.creator {
  max-width: 640px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  align-items: stretch;
}

.creator-preview {
  display: flex;
  justify-content: center;
  padding: 12px 0 28px;
}

.creator-section {
  margin-bottom: 26px;

  .section-title {
    font-size: 14px;
    font-weight: 600;
    color: $text-dark;
    margin: 0 0 12px;
  }
}

.swatch-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;

  &.gradient-row {
    margin-top: 12px;
  }
}

.swatch {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  border: 2px solid transparent;
  cursor: pointer;
  padding: 0;
  box-shadow: inset 0 0 0 2px rgba(255, 255, 255, 0.65);
  transition: transform 0.15s;

  &:hover {
    transform: scale(1.08);
  }

  &.active {
    border-color: #141413;
    transform: scale(1.08);
  }

  &.custom {
    position: relative;
    overflow: hidden;
    background: conic-gradient(#f9705c, #f5b13f, #3fbe86, #5b95f0, #9a72ee, #f9705c);

    input[type='color'] {
      position: absolute;
      inset: -4px;
      opacity: 0;
      cursor: pointer;
    }
  }

  &.gradient {
    border-radius: 10px;

    input[type='color'] {
      position: absolute;
      inset: -4px;
      opacity: 0;
      cursor: pointer;
    }
  }
}

.grad-picker,
.grad-angle {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #6e6b62;

  input[type='color'] {
    width: 26px;
    height: 26px;
    padding: 0;
    border: 1px solid #e8e6dc;
    border-radius: 8px;
    background: transparent;
    cursor: pointer;
  }

  input[type='range'] {
    width: 120px;
    accent-color: $primary-color;
  }
}

.shape-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
}

.shape-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 14px 0 10px;
  border: 1.5px solid #e8e6dc;
  border-radius: 14px;
  background: transparent;
  cursor: pointer;
  transition: border-color 0.15s, background 0.15s;

  &:hover {
    background: rgba($primary-color, 0.05);
  }

  &.active {
    border-color: $primary-color;
    background: rgba($primary-color, 0.07);
  }

  .shape-label {
    font-size: 12px;
    color: #6e6b62;
  }
}

.creator-footer {
  display: flex;
  justify-content: center;
  padding: 8px 0 24px;

  .start-btn {
    min-width: 220px;
    height: 48px;
    border-radius: 24px;
    font-size: 16px;
    font-weight: 600;
  }
}
</style>
