<template>
  <div class="dash-page-header">
    <div class="header-content">
      <div class="header-left">
        <span v-if="backLabel" class="back-btn" @click="goBack">
          <h2>{{ backLabel }}</h2>
        </span>
        <h2 v-if="backLabel" class="separator">/</h2>
        <h2 :class="{ clickable: titleClickable }" @click="titleClickable && emit('title-click')">
          {{ title }}
        </h2>
      </div>
      <div class="header-right">
        <slot name="actions" />
      </div>
    </div>
    <p v-if="description" class="header-desc">{{ description }}</p>
    <div class="header-line"></div>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'

const props = withDefaults(defineProps<{
  /** 当前页标题 */
  title: string
  /** 面包屑前置文案，传入后点击可返回 */
  backLabel?: string
  /** 点击面包屑返回的路由，缺省回首页 */
  backTo?: string
  /** 标题下方的描述文字 */
  description?: string
  /** 标题是否可点击（如智能体名称点击改信息） */
  titleClickable?: boolean
}>(), {
  backLabel: '',
  backTo: '',
  description: '',
  titleClickable: false
})

const emit = defineEmits<{
  (e: 'title-click'): void
}>()

const router = useRouter()

function goBack(): void {
  router.push(props.backTo || '/dashboard')
}
</script>

<style lang="scss" scoped>
.dash-page-header {
  flex-shrink: 0;

  .header-content {
    padding: 16px 24px;
    display: flex;
    justify-content: space-between;
    align-items: center;

    .header-left {
      display: flex;
      align-items: center;
      gap: 12px;
      min-width: 0;

      h2 {
        font-size: 24px;
        font-weight: 600;
        color: #141413;
        margin: 0;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
      }

      .separator {
        color: #141413;
        font-weight: 400;
      }

      .back-btn {
        display: flex;
        align-items: center;
        cursor: pointer;

        h2 {
          color: #5f5d55;
          font-weight: 400;
        }

        &:hover h2 {
          color: $primary-color;
        }
      }

      .clickable {
        cursor: pointer;
        transition: color 0.3s;

        &:hover {
          color: $primary-color;
        }
      }
    }

    .header-right {
      display: flex;
      align-items: center;
      gap: 8px;
      flex-shrink: 0;
    }
  }

  .header-desc {
    padding: 0 24px 12px;
    margin: 0;
    color: #5f5d55;
    font-size: 14px;
  }

  .header-line {
    height: 1px;
    background: #e8e6dc;
  }
}
</style>
