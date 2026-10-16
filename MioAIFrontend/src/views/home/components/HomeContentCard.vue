<template>
  <router-link class="content-card" :to="card.to">
    <div class="card-top">
      <div class="card-icon">
        <BotIcon
          v-if="icon"
          :shape="icon.shape"
          :fill="icon.fill"
          :size="40"
          :live="false"
          eye-color="#faf9f5"
        />
        <img v-else-if="card.avatar" :src="card.avatar" alt="" />
        <span v-else class="card-glyph">
          <component :is="glyphIcon" />
        </span>
      </div>
      <span class="pool-tag">{{ poolLabel }}</span>
    </div>

    <h3 class="card-title">{{ card.title }}</h3>
    <p class="card-desc">{{ card.description }}</p>
    <p v-if="card.meta" class="card-meta">{{ card.meta }}</p>
  </router-link>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { BookOutlined, RobotOutlined, SettingOutlined, ToolOutlined } from '@ant-design/icons-vue'
import BotIcon from '@/components/bot-icon/BotIcon.vue'
import { parseBotIcon } from '@/components/bot-icon/types'
import { POOL_LABELS, type HomeItem } from '../composables/useHomeContent'

const props = defineProps<{
  card: HomeItem
}>()

const icon = computed(() => parseBotIcon(props.card.icon))

const glyphIcon = computed(() => {
  if (props.card.pool === 'knowledge') return BookOutlined
  if (props.card.pool === 'mcp') return ToolOutlined
  if (props.card.pool === 'skill') return SettingOutlined
  return RobotOutlined
})

const poolLabel = computed(() => POOL_LABELS[props.card.pool])
</script>

<style lang="scss" scoped>
.content-card {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: 20px 20px 18px;
  border-radius: 16px;
  background: var(--hm-bg-raised);
  border: 1px solid var(--hm-border-subtle);
  transition: border-color 0.25s var(--hm-ease), box-shadow 0.25s var(--hm-ease),
    transform 0.25s var(--hm-ease);

  &:hover {
    border-color: var(--hm-border);
    box-shadow: 0 10px 28px -16px rgba(20, 20, 19, 0.24);
    transform: translateY(-2px);

    .card-title {
      color: var(--hm-green-deep);
    }
  }
}

.card-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.card-icon {
  display: flex;
  align-items: center;
  min-height: 40px;

  img {
    width: 40px;
    height: 40px;
    border-radius: 11px;
    object-fit: cover;
  }

  .card-glyph {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 40px;
    height: 40px;
    border-radius: 12px;
    background: var(--hm-green-soft);
    color: var(--hm-green-deep);
    font-size: 18px;
  }
}

.pool-tag {
  flex-shrink: 0;
  padding: 3px 10px;
  border-radius: 999px;
  background: rgba(20, 20, 19, 0.04);
  font-size: 12px;
  color: var(--hm-text-muted);
}

.card-title {
  margin-top: 14px;
  font-family: var(--hm-font-text);
  font-size: 16.5px;
  font-weight: 600;
  line-height: 1.4;
  color: var(--hm-text);
  transition: color 0.25s var(--hm-ease);
  // 长名字截断，保证同一行卡片等高
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 无描述时留一行占位高度，避免同一行卡片高度参差 */
.card-desc {
  margin-top: 8px;
  min-height: 2.9em;
  font-size: 14px;
  line-height: 1.72;
  color: var(--hm-text-2);
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  overflow: hidden;
}

.card-meta {
  margin-top: auto;
  padding-top: 12px;
  font-size: 12.5px;
  color: var(--hm-text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
