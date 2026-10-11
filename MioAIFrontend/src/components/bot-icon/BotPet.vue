<template>
  <button type="button" class="bot-pet" :style="petStyle" :aria-label="label" @click="handleClick">
    <BotIcon
      ref="iconRef"
      :shape="config.shape"
      :fill="config.fill"
      :state="displayMood"
      :size="size"
      follow
      :eye-color="eyeColor"
    />
    <span v-if="moodTip" class="bot-pet-tip">{{ moodTip }}</span>
  </button>
</template>

<script setup lang="ts">
/**
 * 聊天界面左下角的常驻机器人：眼睛跟随指针，状态随会话执行情况实时切换，
 * 点击会转一圈并撒花。情绪由 useBotMood 派生（39 态引擎状态机的子集映射）。
 */
import { computed, ref, watch } from 'vue'
import BotIcon from './BotIcon.vue'
import type { BotIconConfig } from './types'

const props = withDefaults(
  defineProps<{
    config: BotIconConfig
    /** 当前情绪状态（39 态之一） */
    mood?: string
    /** 变化计数器：每次 +1 表示该转一圈撒花了（回合完成等里程碑） */
    fxSignal?: number
    size?: number
    eyeColor?: string
    label?: string
  }>(),
  { mood: 'idle', fxSignal: 0, size: 72, eyeColor: '#faf9f5', label: '助手' }
)

const iconRef = ref<InstanceType<typeof BotIcon> | null>(null)
/** 点击/庆祝时的临时情绪，短暂覆盖外部 mood */
const overrideMood = ref<string | null>(null)
let overrideTimer: number | undefined

const displayMood = computed(() => overrideMood.value || props.mood)

const petStyle = computed(() => ({ width: `${props.size + 24}px`, height: `${props.size + 24}px` }))

/** 状态语义提示（仅用户能理解当前在干嘛时才显示） */
const MOOD_TIPS: Record<string, string> = {
  listening: '在听…',
  thinking: '思考中…',
  searching: '搜索中…',
  working: '处理中…',
  writing: '编写中…',
  loading: '加载中…',
  celebrate: '搞定！',
  alerting: '出错了',
  curious: '等你回答',
  drowsy: '有点困…',
  sleeping: '呼呼…'
}
const moodTip = computed(() => MOOD_TIPS[displayMood.value] || '')

watch(
  () => props.fxSignal,
  () => {
    iconRef.value?.spinOnce(1)
    window.setTimeout(() => iconRef.value?.burstOnce(), 620)
  }
)

function handleClick(): void {
  if (overrideTimer) window.clearTimeout(overrideTimer)
  overrideMood.value = 'excited'
  iconRef.value?.spinOnce(1)
  iconRef.value?.burstOnce()
  overrideTimer = window.setTimeout(() => {
    overrideMood.value = null
  }, 2200)
}
</script>

<style lang="scss" scoped>
.bot-pet {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  border: none;
  background: transparent;
  cursor: pointer;
  -webkit-tap-highlight-color: transparent;

  &:focus-visible {
    outline: 2px solid rgba($primary-color, 0.5);
    border-radius: 50%;
  }

  .bot-pet-tip {
    position: absolute;
    left: 50%;
    bottom: calc(100% + 6px);
    transform: translateX(-50%);
    padding: 4px 10px;
    border-radius: 12px;
    background: rgba(20, 20, 19, 0.78);
    color: #faf9f5;
    font-size: 12px;
    white-space: nowrap;
    pointer-events: none;
    animation: pet-tip-in 0.22s ease-out;
  }
}

@keyframes pet-tip-in {
  from {
    opacity: 0;
    transform: translate(-50%, 4px);
  }
  to {
    opacity: 1;
    transform: translate(-50%, 0);
  }
}
</style>
