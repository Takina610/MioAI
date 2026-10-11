<template>
  <div class="playground">
    <h2 class="pg-title">机器人图标调试台</h2>

    <div class="pg-layout">
      <!-- 舞台 -->
      <section class="pg-stage">
        <BotIcon
          ref="iconRef"
          :shape="shape"
          :fill="fill"
          :state="state"
          :size="180"
          :follow="follow"
          eye-color="#f0eee6"
        />
        <div class="pg-state-name">{{ state }}</div>
        <div class="pg-actions">
          <a-button size="small" @click="iconRef?.spinOnce(1)">转一圈</a-button>
          <a-button size="small" @click="iconRef?.bounceOnce()">跳跃</a-button>
          <a-button size="small" @click="iconRef?.burstOnce()">撒花</a-button>
          <a-button size="small" :type="follow ? 'primary' : 'default'" @click="follow = !follow">
            眼睛跟随
          </a-button>
        </div>
      </section>

      <!-- 状态（引擎 39 态全量） -->
      <section class="pg-panel">
        <h3 class="pg-section">状态（{{ stateCount }} 个，来自引擎状态表）</h3>
        <div v-for="group in GROK_TABLES.GROUPS" :key="group.label" class="pg-group">
          <div class="pg-group-label">
            {{ group.label }}
            <span class="pg-group-hint">{{ groupHint(group.states) }}</span>
          </div>
          <div class="pg-chips">
            <button
              v-for="s in group.states"
              :key="s"
              type="button"
              class="pg-chip"
              :class="{ active: state === s }"
              :title="stateHint(s)"
              @click="state = s"
            >
              {{ s }}
            </button>
          </div>
        </div>
      </section>

      <!-- 形体（引擎 18 形体全量） -->
      <section class="pg-panel">
        <h3 class="pg-section">形体（{{ shapeCount }} 个）</h3>
        <div class="pg-shapes">
          <button
            v-for="(def, name) in GROK_GEO.shapes"
            :key="name"
            type="button"
            class="pg-shape"
            :class="{ active: shape === name }"
            @click="shape = String(name)"
          >
            <BotIcon :shape="String(name)" :fill="fill" :size="34" :live="false" eye-color="#f0eee6" />
            <span>{{ name }}</span>
          </button>
        </div>
      </section>

      <!-- 颜色 -->
      <section class="pg-panel">
        <h3 class="pg-section">填充</h3>
        <div class="pg-chips">
          <button
            v-for="p in BOT_PALETTE"
            :key="p.id"
            type="button"
            class="pg-swatch"
            :class="{ active: fill.kind === 'palette' && fill.id === p.id }"
            :style="{ background: p.color }"
            @click="fill = { kind: 'palette', id: p.id }"
          />
          <button
            type="button"
            class="pg-swatch"
            :class="{ active: fill.kind === 'linear' }"
            :style="{ background: 'linear-gradient(135deg, #2aa1a9, #0e74e0)' }"
            @click="fill = { kind: 'linear', from: '#2aa1a9', to: '#0e74e0', angle: 135 }"
          />
          <button
            type="button"
            class="pg-swatch"
            :class="{ active: fill.kind === 'radial' }"
            :style="{ background: 'radial-gradient(circle at 50% 42%, #2aa1a9, #0e74e0)' }"
            @click="fill = { kind: 'radial', from: '#2aa1a9', to: '#0e74e0' }"
          />
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 引擎全量调试台：39 种状态 × 18 种形体 × 全部填充方式逐一点选验证，
 * 状态旁标注该状态是否具备专属姿态/覆盖图形（其余走默认姿态，眼睛播放表仍生效）。
 */
import { computed, ref } from 'vue'
import BotIcon from '@/components/bot-icon/BotIcon.vue'
import { GROK_TABLES } from '@/components/bot-icon/engine/tables'
import { GROK_GEO } from '@/components/bot-icon/engine/geometry'
import { GROK_FX } from '@/components/bot-icon/engine/fx'
import { BOT_PALETTE } from '@/components/bot-icon/types'
import type { BotFill } from '@/components/bot-icon/types'

const state = ref('idle')
const shape = ref('blob')
const fill = ref<BotFill>({ kind: 'palette', id: 'black' })
const follow = ref(true)
const iconRef = ref<InstanceType<typeof BotIcon> | null>(null)

const stateCount = computed(() =>
  GROK_TABLES.GROUPS.reduce((n, g) => n + g.states.length, 0)
)
const shapeCount = computed(() => Object.keys(GROK_GEO.shapes).length)

function stateHint(s: string): string {
  const overlayMap = GROK_FX.MAP as Record<string, string>
  const eyes = (GROK_TABLES.EYE_PLAYLIST as Record<string, number[]>)[s] ?? []
  const overlay = overlayMap[s] ? `覆盖图形 ${overlayMap[s]}` : '无覆盖图形'
  return `眼睛播放表 [${eyes.join(', ')}]；${overlay}`
}

function groupHint(states: string[]): string {
  const withPose = states.filter(s => POSE_STATES.has(s)).length
  return `${withPose}/${states.length} 专属姿态`
}

// applyPose switch 有专属姿态的状态集合（其余走默认姿态，眼睛与覆盖图形仍生效）
const POSE_STATES = new Set([
  'sleeping', 'waking', 'idle', 'listening', 'thinking', 'searching', 'working',
  'excited', 'surprised', 'suspicious', 'angry', 'drowsy', 'happy', 'curious',
  'confused', 'bored', 'proud', 'shy', 'sad', 'laughing', 'scared', 'playful',
  'celebrate', 'dragging', 'humming', 'notifying'
])
</script>

<style lang="scss" scoped>
.playground {
  max-width: 1100px;
  margin: 0 auto;
  padding: 24px 16px 48px;
}

.pg-title {
  font-size: 18px;
  font-weight: 700;
  margin: 0 0 20px;
}

.pg-layout {
  display: grid;
  grid-template-columns: 300px 1fr;
  gap: 28px;
  align-items: start;
}

.pg-stage {
  position: sticky;
  top: 24px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
  padding: 24px 16px;
  border: 1px solid #e8e6dc;
  border-radius: 16px;
  background: #f0eee6;
}

.pg-state-name {
  font-family: ui-monospace, Consolas, monospace;
  font-size: 14px;
  color: #141413;
}

.pg-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: center;
}

.pg-panel {
  margin-bottom: 24px;

  .pg-section {
    font-size: 14px;
    font-weight: 600;
    margin: 0 0 10px;
  }
}

.pg-group {
  margin-bottom: 12px;
}

.pg-group-label {
  font-size: 12px;
  color: #8c8a82;
  margin-bottom: 6px;

  .pg-group-hint {
    margin-left: 8px;
    color: #b5b2a6;
  }
}

.pg-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.pg-chip {
  padding: 4px 10px;
  font-size: 12px;
  font-family: ui-monospace, Consolas, monospace;
  border: 1px solid #e8e6dc;
  border-radius: 12px;
  background: #fff;
  cursor: pointer;

  &:hover {
    border-color: $primary-color;
  }

  &.active {
    background: rgba($primary-color, 0.1);
    border-color: $primary-color;
    color: $primary-color;
  }
}

.pg-shapes {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(76px, 1fr));
  gap: 8px;
}

.pg-shape {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 8px 0 6px;
  border: 1.5px solid #e8e6dc;
  border-radius: 10px;
  background: #fff;
  cursor: pointer;
  font-size: 11px;
  color: #6e6b62;

  &.active {
    border-color: $primary-color;
    background: rgba($primary-color, 0.06);
  }
}

.pg-swatch {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  border: 2px solid transparent;
  cursor: pointer;
  padding: 0;
  box-shadow: inset 0 0 0 2px rgba(255, 255, 255, 0.65);

  &.active {
    border-color: #141413;
  }
}
</style>
