<template>
  <div class="filter-switch">
    <input id="option1" value="dashscope" name="model-provider" type="radio" v-model="modelProvider" @change="handleModelChange" />
    <label class="option" for="option1">云端模型</label>
    <input id="option2" value="ollama" name="model-provider" type="radio" v-model="modelProvider" @change="handleModelChange" />
    <label class="option" for="option2">本地模型</label>
    <span class="background"></span>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'

const STORAGE_KEY = 'ai-model-provider'

const modelProvider = ref<string>(localStorage.getItem(STORAGE_KEY) || 'dashscope')

function handleModelChange(): void {
  localStorage.setItem(STORAGE_KEY, modelProvider.value)
}
</script>

<style lang="scss" scoped>
/* From Uiverse.io by Xtenso */
.filter-switch {
  border: 2px solid #2aa1a9;
  border-radius: 30px;
  position: relative;
  display: flex;
  align-items: center;
  height: 36px;
  width: 240px;
  overflow: hidden;

  input {
    display: none;
  }

  label {
    flex: 1;
    text-align: center;
    cursor: pointer;
    border: none;
    border-radius: 30px;
    position: relative;
    overflow: hidden;
    z-index: 1;
    transition: all 0.5s;
    font-weight: 500;
    font-size: 14px;
  }

  .background {
    position: absolute;
    width: 49%;
    height: 28px;
    background-color: #2aa1a9;
    top: 3px;
    left: 3px;
    border-radius: 30px;
    transition: left 0.4s cubic-bezier(0.175, 0.885, 0.32, 1.275);
  }

  #option2:checked ~ .background {
    left: 50%;
  }

  #option1:checked + label[for="option1"],
  #option2:checked + label[for="option2"] {
    color: #ece9de;
    font-weight: bold;
  }

  #option1:not(:checked) + label[for="option1"],
  #option2:not(:checked) + label[for="option2"] {
    color: #7d7d7d;
  }
}
</style>
