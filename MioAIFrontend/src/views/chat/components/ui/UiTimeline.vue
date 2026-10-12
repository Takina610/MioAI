<template>
  <div class="ui-timeline">
    <div v-for="(event, i) in events" :key="i" class="ui-tl-event">
      <span class="ui-tl-dot"></span>
      <div class="ui-tl-body">
        <div class="ui-tl-head">
          <span v-if="event.time" class="ui-tl-time">{{ event.time }}</span>
          <span class="ui-tl-title">{{ event.title }}</span>
        </div>
        <div v-if="event.desc" class="ui-tl-desc">{{ event.desc }}</div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { MessageBlock, UiTimelineEvent } from '@/types'

type UiBlock = Extract<MessageBlock, { type: 'ui' }>

const props = defineProps<{ block: UiBlock; chatId?: string }>()

const events = computed<UiTimelineEvent[]>(() =>
  (props.block.events ?? []).filter(e => e && e.title)
)
</script>

<style scoped lang="scss">
.ui-tl-event {
  position: relative;
  display: flex;
  gap: 12px;
  padding: 0 0 14px 2px;

  /* 时间线连接线（首尾不伸出） */
  &::before {
    content: '';
    position: absolute;
    left: 4px;
    top: 12px;
    bottom: -4px;
    width: 1px;
    background: $border-warm-subtle;
  }

  &:last-child {
    padding-bottom: 2px;

    &::before { display: none; }
  }
}

.ui-tl-dot {
  position: relative;
  z-index: 1;
  width: 9px;
  height: 9px;
  margin-top: 5px;
  flex-shrink: 0;
  background: $primary-color;
  border-radius: 50%;
}

.ui-tl-body {
  flex: 1;
  min-width: 0;
}

.ui-tl-head {
  display: flex;
  align-items: baseline;
  gap: 8px;
  flex-wrap: wrap;
}

.ui-tl-time {
  font-size: 12px;
  color: $primary-color;
  font-variant-numeric: tabular-nums;
}

.ui-tl-title {
  font-size: 13px;
  font-weight: 600;
  color: $text-secondary;
  line-height: 1.5;
}

.ui-tl-desc {
  margin-top: 3px;
  font-size: 12px;
  line-height: 1.6;
  color: $text-muted;
}
</style>
