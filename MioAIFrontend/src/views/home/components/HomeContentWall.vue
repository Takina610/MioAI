<template>
  <section v-if="filtered.length > 0" class="content-wall">
    <div class="home-container">
      <header class="wall-head">
        <h2 class="wall-title">站内内容</h2>
        <div class="pool-filter" role="tablist" aria-label="内容类型筛选">
          <button
            v-for="option in filterOptions"
            :key="option.key"
            type="button"
            class="pool-chip"
            :class="{ active: activePool === option.key }"
            role="tab"
            :aria-selected="activePool === option.key"
            @click="activePool = option.key"
          >
            {{ option.label }}
            <span v-if="option.count > 0" class="chip-count">{{ option.count }}</span>
          </button>
        </div>
      </header>

      <div class="wall-grid">
        <HomeContentCard v-for="item in filtered" :key="item.key" :card="item" />
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import HomeContentCard from './HomeContentCard.vue'
import { POOL_LABELS, type HomeItem, type PoolKey } from '../composables/useHomeContent'

const props = defineProps<{
  items: HomeItem[]
}>()

const activePool = ref<PoolKey | 'all'>('all')

/** 筛选按钮：只列出实际有内容的池，计数为池内条数 */
const filterOptions = computed(() => {
  const counts = new Map<PoolKey, number>()
  props.items.forEach((item) => counts.set(item.pool, (counts.get(item.pool) ?? 0) + 1))
  return [
    { key: 'all' as const, label: '全部', count: props.items.length },
    ...(Object.keys(POOL_LABELS) as PoolKey[])
      .filter(pool => counts.has(pool))
      .map(pool => ({ key: pool, label: POOL_LABELS[pool], count: counts.get(pool) ?? 0 }))
  ]
})

const filtered = computed(() =>
  activePool.value === 'all' ? props.items : props.items.filter(item => item.pool === activePool.value)
)
</script>

<style lang="scss" scoped>
.content-wall {
  padding: clamp(40px, 5vw, 64px) 0 0;
}

.wall-head {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: 16px 24px;
  margin-bottom: clamp(20px, 2.5vw, 30px);
}

.wall-title {
  font-family: var(--hm-font-display);
  font-size: clamp(22px, 2.4vw, 30px);
  font-weight: 700;
  letter-spacing: 0.01em;
  color: var(--hm-text);
}

.pool-filter {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.pool-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 7px 16px;
  border-radius: 999px;
  border: 1px solid var(--hm-border);
  background: transparent;
  font-family: var(--hm-font-text);
  font-size: 13.5px;
  font-weight: 500;
  color: var(--hm-text-2);
  cursor: pointer;
  transition: border-color 0.22s var(--hm-ease), background 0.22s var(--hm-ease),
    color 0.22s var(--hm-ease);

  &:hover {
    border-color: var(--hm-green);
    color: var(--hm-green-deep);
  }

  &.active {
    border-color: var(--hm-green);
    background: var(--hm-green);
    color: #fff;
  }

  .chip-count {
    font-size: 12px;
    font-variant-numeric: tabular-nums;
    opacity: 0.7;
  }
}

.wall-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
  align-items: stretch;
}
</style>
