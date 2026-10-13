<template>
  <a-modal
    :open="visible"
    title="编辑群聊"
    :footer="null"
    :width="520"
    centered
    wrap-class-name="group-edit-modal"
    @update:open="(v: boolean) => emit('update:visible', v)"
  >
    <div class="group-edit-body">
      <a-input v-model:value="name" placeholder="群聊名称" :maxlength="32" class="name-input" />

      <div class="member-count">{{ members.length }} 个成员 · 至少保留 2 个</div>

      <div class="member-list">
        <div v-for="m in members" :key="m.id" class="member-item">
          <span class="member-icon">
            <BotIcon
              v-if="iconOf(m)"
              :shape="iconOf(m)!.shape"
              :fill="iconOf(m)!.fill"
              :size="26"
              :live="false"
            />
            <span v-else class="member-initial">{{ m.name?.charAt(0)?.toUpperCase() }}</span>
          </span>
          <span class="member-name">{{ m.name }}</span>
          <button
            class="member-remove"
            title="移除成员"
            aria-label="移除成员"
            :disabled="members.length <= 2"
            @click="removeMember(m.id)"
          >
            <CloseCircleOutlined />
          </button>
        </div>
        <div v-if="!members.length" class="member-empty">成员不足，无法保存</div>
      </div>

      <!-- 添加成员：我的自定义智能体中尚未入群的，点击即加入 -->
      <div v-if="addableCandidates.length" class="add-section">
        <div class="add-title">添加成员</div>
        <div class="member-list add-list">
          <div v-for="a in addableCandidates" :key="a.id" class="member-item add" @click="addMember(a)">
            <span class="member-icon">
              <BotIcon
                v-if="iconOf(a)"
                :shape="iconOf(a)!.shape"
                :fill="iconOf(a)!.fill"
                :size="26"
                :live="false"
              />
              <span v-else class="member-initial">{{ a.name?.charAt(0)?.toUpperCase() }}</span>
            </span>
            <span class="member-name">{{ a.name }}</span>
            <PlusCircleOutlined class="member-add-icon" />
          </div>
        </div>
      </div>

      <div class="modal-actions">
        <a-button @click="close">取消</a-button>
        <a-button type="primary" :loading="saving" :disabled="!canSubmit" @click="submit">
          保存
        </a-button>
      </div>
    </div>
  </a-modal>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { CloseCircleOutlined, PlusCircleOutlined } from '@ant-design/icons-vue'
import BotIcon from '@/components/bot-icon/BotIcon.vue'
import { parseBotIcon, type BotIconConfig } from '@/components/bot-icon/types'
import { updateAgentGroup } from '@/api/agent'
import type { Agent, AgentMemberBrief } from '@/types'

const props = defineProps<{
  visible: boolean
  /** 当前群聊（含 members 概要） */
  group: Agent | null
  /** 候选成员：我的自定义智能体（用于添加成员） */
  candidates: Agent[]
}>()

const emit = defineEmits<{
  (e: 'update:visible', v: boolean): void
  (e: 'saved'): void
}>()

const name = ref('')
const members = ref<AgentMemberBrief[]>([])
const saving = ref(false)

const canSubmit = computed(() =>
  name.value.trim().length > 0 && members.value.length >= 2 && !saving.value)

const iconCache = new Map<number, BotIconConfig | null>()
function iconOf(m: AgentMemberBrief): BotIconConfig | null {
  if (!iconCache.has(m.id)) {
    iconCache.set(m.id, parseBotIcon(m.icon))
  }
  return iconCache.get(m.id) ?? null
}

watch(() => props.visible, v => {
  if (v) {
    name.value = props.group?.name ?? ''
    members.value = [...(props.group?.members ?? [])]
    saving.value = false
  }
})

function removeMember(id: number): void {
  if (members.value.length <= 2) return
  members.value = members.value.filter(m => m.id !== id)
}

/** 尚未入群的候选（点击加入成员列表） */
const addableCandidates = computed(() => {
  const inGroup = new Set(members.value.map(m => m.id))
  return props.candidates.filter(a => !inGroup.has(a.id))
})

function addMember(agent: Agent): void {
  members.value = [...members.value, {
    id: agent.id,
    name: agent.name,
    icon: agent.icon,
    avatar: agent.avatar
  }]
}

function close(): void {
  emit('update:visible', false)
}

async function submit(): Promise<void> {
  if (!canSubmit.value || !props.group) return
  saving.value = true
  try {
    await updateAgentGroup({
      id: props.group.id,
      name: name.value.trim(),
      memberAgentIds: members.value.map(m => m.id)
    })
    message.success('群聊已更新')
    emit('saved')
    close()
  } catch (e) {
    // 接口层已弹出业务错误提示，这里只阻止弹窗关闭
    console.error('更新群聊失败', e)
  } finally {
    saving.value = false
  }
}
</script>

<style lang="scss">
.group-edit-modal {
  .group-edit-body {
    display: flex;
    flex-direction: column;
    gap: 12px;
    padding-top: 4px;
  }

  .name-input {
    height: 40px;
    border-radius: 10px;
  }

  .member-count {
    font-size: 12px;
    color: #8c8a82;
  }

  .member-list {
    max-height: 300px;
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
  }

  .member-icon {
    flex-shrink: 0;
    width: 28px;
    height: 28px;
    display: flex;
    align-items: center;
    justify-content: center;
  }

  .member-initial {
    width: 26px;
    height: 26px;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: 25%;
    background: rgba($primary-color, 0.14);
    color: $primary-color;
    font-size: 12px;
    font-weight: 600;
  }

  .member-name {
    flex: 1;
    min-width: 0;
    font-size: 13px;
    font-weight: 600;
    color: #141413;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .member-remove {
    flex-shrink: 0;
    width: 26px;
    height: 26px;
    display: flex;
    align-items: center;
    justify-content: center;
    border: none;
    border-radius: 8px;
    background: transparent;
    color: #8c8a82;
    font-size: 14px;
    cursor: pointer;
    transition: color 0.2s, background 0.2s;

    &:hover:not(:disabled) {
      color: #d48806;
      background: rgba(212, 136, 6, 0.1);
    }

    &:disabled {
      opacity: 0.35;
      cursor: not-allowed;
    }
  }

  .member-empty {
    padding: 12px;
    font-size: 13px;
    color: #8c8a82;
    text-align: center;
  }

  // 添加成员区
  .add-section {
    .add-title {
      font-size: 12px;
      color: #8c8a82;
    }

    .add-list {
      max-height: 160px;
    }

    .member-item.add {
      cursor: pointer;
      background: #faf9f5;

      &:hover {
        border-color: $primary-color;

        .member-add-icon {
          color: $primary-color;
        }
      }
    }

    .member-add-icon {
      flex-shrink: 0;
      font-size: 15px;
      color: #b0aea5;
      transition: color 0.2s;
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
