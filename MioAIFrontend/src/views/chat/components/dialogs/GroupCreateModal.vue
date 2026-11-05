<template>
  <a-modal
    :open="visible"
    title="创建群聊"
    :footer="null"
    :width="560"
    centered
    wrap-class-name="group-create-modal"
    @update:open="(v: boolean) => emit('update:visible', v)"
  >
    <div class="group-create-body">
      <a-input v-model:value="name" placeholder="群聊名称" :maxlength="32" class="name-input" />
      <div class="selected-count">已选 {{ selectedIds.size }} 个成员</div>

      <div class="member-list">
        <button
          v-for="agent in agents"
          :key="agent.id"
          class="member-item"
          :class="{ selected: selectedIds.has(agent.id) }"
          @click="toggle(agent.id)"
        >
          <span class="member-icon">
            <BotIcon
              v-if="iconByAgent.get(agent.id)"
              :shape="iconByAgent.get(agent.id)!.shape"
              :fill="iconByAgent.get(agent.id)!.fill"
              :size="30"
              :live="false"
            />
            <a-avatar v-else :size="30" :src="agent.avatar">
              {{ agent.name?.charAt(0)?.toUpperCase() }}
            </a-avatar>
          </span>
          <span class="member-meta">
            <span class="member-name">{{ agent.name }}</span>
            <span v-if="agent.description" class="member-desc">{{ agent.description }}</span>
          </span>
          <CheckCircleFilled v-if="selectedIds.has(agent.id)" class="member-check" />
        </button>
      </div>

      <div class="modal-actions">
        <a-button @click="close">取消</a-button>
        <a-button type="primary" :loading="creating" :disabled="!canSubmit" @click="submit">
          创建
        </a-button>
      </div>
    </div>
  </a-modal>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { CheckCircleFilled } from '@ant-design/icons-vue'
import BotIcon from '@/components/agent/bot-icon/BotIcon.vue'
import { parseBotIcon, type BotIconConfig } from '@/components/agent/bot-icon/types'
import { createAgentGroup } from '@/api/agent'
import type { Agent } from '@/types'

const props = defineProps<{
  visible: boolean
  /** 候选成员（我的自定义智能体，群聊不可嵌套） */
  agents: Agent[]
}>()

const emit = defineEmits<{
  (e: 'update:visible', v: boolean): void
  (e: 'success', groupAgentId: number): void
}>()

const name = ref('')
const selectedIds = ref<Set<number>>(new Set())
const creating = ref(false)

const canSubmit = computed(() =>
  name.value.trim().length > 0 && selectedIds.value.size >= 2 && !creating.value)

const iconByAgent = computed(() => {
  const map = new Map<number, BotIconConfig | null>()
  for (const agent of props.agents) {
    map.set(agent.id, parseBotIcon(agent.icon))
  }
  return map
})

watch(() => props.visible, v => {
  if (v) {
    name.value = ''
    selectedIds.value = new Set()
    creating.value = false
  }
})

function toggle(id: number): void {
  const next = new Set(selectedIds.value)
  if (next.has(id)) {
    next.delete(id)
  } else {
    next.add(id)
  }
  selectedIds.value = next
}

function close(): void {
  emit('update:visible', false)
}

async function submit(): Promise<void> {
  if (!canSubmit.value) return
  creating.value = true
  try {
    const groupId = await createAgentGroup({
      name: name.value.trim(),
      memberAgentIds: [...selectedIds.value]
    })
    message.success('群聊已创建')
    emit('success', groupId)
    close()
  } catch (e) {
    // 接口层已弹出业务错误提示，这里只阻止弹窗关闭
    console.error('创建群聊失败', e)
  } finally {
    creating.value = false
  }
}
</script>

<style lang="scss">
.group-create-modal {
  .group-create-body {
    display: flex;
    flex-direction: column;
    gap: 12px;
    padding-top: 4px;
  }

  .name-input {
    height: 40px;
    border-radius: 10px;
  }

  .selected-count {
    font-size: 12px;
    color: #8c8a82;
  }

  .member-list {
    max-height: 320px;
    overflow-y: auto;
    display: flex;
    flex-direction: column;
    gap: 6px;
    padding: 2px;
  }

  .member-item {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 8px 10px;
    border: 1px solid #e8e6dc;
    border-radius: 12px;
    background: #fff;
    cursor: pointer;
    text-align: left;
    transition: border-color 0.2s, background 0.2s;

    &:hover {
      border-color: rgba($primary-color, 0.5);
    }

    &.selected {
      border-color: $primary-color;
      background: rgba($primary-color, 0.07);
    }

    .member-icon {
      flex-shrink: 0;
      width: 32px;
      height: 32px;
      display: flex;
      align-items: center;
      justify-content: center;
    }

    .member-meta {
      flex: 1;
      min-width: 0;
      display: flex;
      flex-direction: column;
      gap: 2px;
    }

    .member-name {
      font-size: 13px;
      font-weight: 600;
      color: #141413;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }

    .member-desc {
      font-size: 12px;
      color: #8c8a82;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }

    .member-check {
      flex-shrink: 0;
      font-size: 16px;
      color: $primary-color;
    }
  }

  .modal-actions {
    display: flex;
    justify-content: flex-end;
    gap: 8px;
    padding-top: 4px;
  }
}
</style>
