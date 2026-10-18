<template>
  <div class="icon-designer">
    <div class="designer-section">
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
        <div class="swatch custom" :class="{ active: state.fill.kind === 'solid' }">
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
          class="grad-btn"
          :class="{ active: state.fill.kind === 'linear' }"
          @click="selectGradient('linear')"
        >
          <span class="grad-swatch" :style="linearPreviewStyle" />
          <span class="grad-label">线性渐变</span>
        </button>
        <button
          type="button"
          class="grad-btn"
          :class="{ active: state.fill.kind === 'radial' }"
          @click="selectGradient('radial')"
        >
          <span class="grad-swatch radial" :style="radialPreviewStyle" />
          <span class="grad-label">径向渐变</span>
        </button>
        <template v-if="state.fill.kind === 'linear' || state.fill.kind === 'radial'">
          <label class="grad-picker">
            <input type="color" :value="gradFrom" @input="setGradColor('from', ($event.target as HTMLInputElement).value)" />
            <span>起点</span>
          </label>
          <label class="grad-picker">
            <input type="color" :value="gradTo" @input="setGradColor('to', ($event.target as HTMLInputElement).value)" />
            <span>终点</span>
          </label>
          <label v-if="state.fill.kind === 'linear'" class="grad-angle">
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
    </div>

    <div class="designer-section">
      <h3 class="section-title">形体</h3>
      <div class="shape-grid">
        <button
          v-for="s in BOT_SHAPES"
          :key="s.id"
          type="button"
          class="shape-item"
          :class="{ active: state.shape === s.id }"
          @click="selectShape(s.id)"
        >
          <BotIcon :shape="s.id" :fill="state.fill" :size="40" :live="false" :eye-color="eyeColor" />
          <span class="shape-label">{{ s.label }}</span>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
/** 机器人图标设计器：颜色（11色板/自定义/线性渐变/径向渐变）+ 形体（8种） */
import { ref, watch } from 'vue'
import BotIcon from './BotIcon.vue'
import { BOT_PALETTE, BOT_SHAPES } from './types'
import type { BotIconConfig, BotPaletteId } from './types'

const props = defineProps<{
  config: BotIconConfig
  /** 迷你形体预览的眼睛底色（与所在表面一致） */
  eyeColor?: string
}>()

const emit = defineEmits<{
  (e: 'change', config: BotIconConfig): void
}>()

// 内部持有最新状态再 emit：父级 props 回流是异步的，连续两次操作若直接展开 props 会用旧值覆盖前一次
const state = ref<BotIconConfig>(props.config)
watch(() => props.config, v => { state.value = v })

function update(next: BotIconConfig): void {
  state.value = next
  emit('change', next)
}

const customColor = ref('#2aa1a9')
const gradFrom = ref('#2aa1a9')
const gradTo = ref('#0e74e0')
const gradAngle = ref(135)

const linearPreviewStyle = ref<Record<string, string>>({
  background: `linear-gradient(135deg, ${gradFrom.value}, ${gradTo.value})`
})
const radialPreviewStyle = ref<Record<string, string>>({
  background: `radial-gradient(circle at 50% 42%, ${gradFrom.value}, ${gradTo.value})`
})

function isActivePalette(id: BotPaletteId): boolean {
  return state.value.fill.kind === 'palette' && state.value.fill.id === id
}

function selectPalette(id: BotPaletteId): void {
  update({ ...state.value, fill: { kind: 'palette', id } })
}

function selectCustom(color: string): void {
  customColor.value = color
  update({ ...state.value, fill: { kind: 'solid', color } })
}

function selectGradient(kind: 'linear' | 'radial'): void {
  if (kind === 'linear') {
    update({ ...state.value, fill: { kind: 'linear', from: gradFrom.value, to: gradTo.value, angle: gradAngle.value } })
  } else {
    update({ ...state.value, fill: { kind: 'radial', from: gradFrom.value, to: gradTo.value } })
  }
}

function setGradColor(part: 'from' | 'to', color: string): void {
  if (part === 'from') gradFrom.value = color
  else gradTo.value = color
  linearPreviewStyle.value = { background: `linear-gradient(135deg, ${gradFrom.value}, ${gradTo.value})` }
  radialPreviewStyle.value = { background: `radial-gradient(circle at 50% 42%, ${gradFrom.value}, ${gradTo.value})` }
  selectGradient(state.value.fill.kind === 'radial' ? 'radial' : 'linear')
}

function setGradAngle(angle: number): void {
  gradAngle.value = angle
  if (state.value.fill.kind === 'linear') {
    update({ ...state.value, fill: { kind: 'linear', from: gradFrom.value, to: gradTo.value, angle } })
  }
}

function selectShape(id: string): void {
  update({ ...state.value, shape: id })
}
</script>

<style lang="scss" scoped>
.icon-designer {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.designer-section {
  .section-title {
    font-size: 13px;
    font-weight: 600;
    color: $text-dark;
    margin: 0 0 10px;
  }
}

.swatch-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;

  &.gradient-row {
    margin-top: 10px;
  }
}

.swatch {
  width: 30px;
  height: 30px;
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
}

.grad-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 5px 12px 5px 6px;
  border: 1.5px solid #e8e6dc;
  border-radius: 20px;
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

  .grad-swatch {
    width: 22px;
    height: 22px;
    border-radius: 50%;
    box-shadow: inset 0 0 0 1.5px rgba(255, 255, 255, 0.5);
  }

  .grad-label {
    font-size: 13px;
    color: $text-dark;
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
    width: 24px;
    height: 24px;
    padding: 0;
    border: 1px solid #e8e6dc;
    border-radius: 8px;
    background: transparent;
    cursor: pointer;
  }

  input[type='range'] {
    width: 100px;
    accent-color: $primary-color;
  }
}

.shape-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
}

.shape-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 10px 0 8px;
  border: 1.5px solid #e8e6dc;
  border-radius: 12px;
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
</style>
