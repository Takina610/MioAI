<template>
  <a-modal
    :open="visible"
    title="MioBot 个性化设置"
    :mask-closable="false"
    width="620px"
    :confirm-loading="saving"
    @update:open="handleVisibleChange"
    @ok="handleSave"
    @cancel="handleVisibleChange(false)"
    ok-text="保存"
    cancel-text="取消"
    wrap-class-name="agent-personalize-modal"
  >
    <div class="personalize-body">
      <div class="icon-designer">
        <BotIcon
          :shape="iconConfig.shape"
          :fill="iconConfig.fill"
          state="idle"
          :size="72"
          follow
          mood-cycle
          eye-color="#faf9f5"
        />
      </div>
      <BotIconDesigner :config="iconConfig" eye-color="#faf9f5" @change="iconConfig = $event" />

      <div class="prompt-field">
        <span class="field-label">人设提示词</span>
        <a-textarea
          v-model:value="formData.systemPrompt"
          placeholder="未设置时使用内置默认"
          :auto-size="{ minRows: 4, maxRows: 12 }"
          :maxlength="10000"
          show-count
        />
      </div>

      <div class="public-field">
        <span class="field-label">公开</span>
        <a-switch
          :checked="formData.isPublic === 1"
          @change="(checked: any) => (formData.isPublic = checked ? 1 : 0)"
        />
      </div>
    </div>

    <template #footer>
      <div class="personalize-footer">
        <a-popconfirm
          v-if="personalized"
          title="恢复内置默认？当前个性化配置将被删除"
          ok-text="恢复默认"
          cancel-text="取消"
          @confirm="handleReset"
        >
          <a-button danger :loading="resetting">恢复默认</a-button>
        </a-popconfirm>
        <div class="footer-main">
          <a-button @click="handleVisibleChange(false)">取消</a-button>
          <a-button type="primary" :loading="saving" @click="handleSave">保存</a-button>
        </div>
      </div>
    </template>
  </a-modal>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import BotIcon from '@/components/bot-icon/BotIcon.vue'
import BotIconDesigner from '@/components/bot-icon/BotIconDesigner.vue'
import { DEFAULT_BOT_ICON, parseBotIcon, serializeBotIcon } from '@/components/bot-icon/types'
import type { BotIconConfig } from '@/components/bot-icon/types'
import { getMyAgentConfig, saveMyAgentConfig, resetMyAgentConfig } from '@/api/agent'
import type { Agent } from '@/types'

const props = defineProps<{
  visible: boolean
  agent: Agent | null
}>()

const emit = defineEmits<{
  (e: 'update:visible', visible: boolean): void
  (e: 'saved'): void
}>()

const saving = ref(false)
const resetting = ref(false)
const personalized = ref(false)
const iconConfig = ref<BotIconConfig>({ ...DEFAULT_BOT_ICON })

const formData = reactive({
  systemPrompt: '',
  isPublic: 0
})

watch(() => props.visible, async (visible) => {
  if (!visible || !props.agent) return
  try {
    const config = await getMyAgentConfig(props.agent.id)
    personalized.value = !!config
    formData.systemPrompt = config?.systemPrompt || ''
    formData.isPublic = config?.isPublic ?? 0
    iconConfig.value = parseBotIcon(config?.icon) ?? parseBotIcon(props.agent.icon) ?? { ...DEFAULT_BOT_ICON }
  } catch (e) {
    console.error(e)
    message.error('获取个性化配置失败')
  }
})

function handleVisibleChange(visible: boolean): void {
  emit('update:visible', visible)
}

async function handleSave(): Promise<void> {
  if (!props.agent || saving.value) return
  try {
    saving.value = true
    await saveMyAgentConfig(props.agent.id, {
      systemPrompt: formData.systemPrompt.trim() || undefined,
      icon: iconEqualsDefault(iconConfig.value) ? undefined : serializeBotIcon(iconConfig.value),
      isPublic: formData.isPublic
    })
    message.success('已保存')
    emit('update:visible', false)
    emit('saved')
  } catch (e) {
    console.error(e)
    message.error('保存失败')
  } finally {
    saving.value = false
  }
}

async function handleReset(): Promise<void> {
  if (!props.agent || resetting.value) return
  try {
    resetting.value = true
    await resetMyAgentConfig(props.agent.id)
    message.success('已恢复内置默认')
    emit('update:visible', false)
    emit('saved')
  } catch (e) {
    console.error(e)
    message.error('恢复默认失败')
  } finally {
    resetting.value = false
  }
}

/** 与默认图标完全一致视为未个性化，不落覆盖行 */
function iconEqualsDefault(config: BotIconConfig): boolean {
  return serializeBotIcon(config) === serializeBotIcon({ ...DEFAULT_BOT_ICON })
}
</script>

<style lang="scss">
.agent-personalize-modal {
  .personalize-body {
    display: flex;
    flex-direction: column;
    gap: 16px;
    max-height: 65vh;
    overflow: auto;
    padding-right: 4px;

    .icon-designer {
      display: flex;
      justify-content: center;
      padding-top: 4px;
    }

    .prompt-field {
      .field-label {
        display: block;
        font-size: 13px;
        font-weight: 600;
        color: $text-dark;
        margin-bottom: 8px;
      }
    }

    .public-field {
      display: flex;
      align-items: center;
      gap: 12px;

      .field-label {
        font-size: 13px;
        font-weight: 600;
        color: $text-dark;
      }
    }
  }

  .personalize-footer {
    display: flex;
    align-items: center;
    justify-content: space-between;

    .footer-main {
      display: flex;
      gap: 8px;
      margin-left: auto;
    }
  }
}
</style>
