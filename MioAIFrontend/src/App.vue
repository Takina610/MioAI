<template>
  <a-config-provider :theme="antdTheme">
    <router-view />
  </a-config-provider>
</template>

<script setup lang="ts">
import { theme } from 'ant-design-vue'

/** 全站 antd 主题：Anthropic 暖色中性板 + 品牌绿 + 官方字体栈 */
const antdTheme = {
  algorithm: theme.defaultAlgorithm,
  token: {
    colorPrimary: '#2aa1a9',
    colorInfo: '#2aa1a9',
    colorLink: '#2aa1a9',
    colorTextBase: '#141413',
    colorBgLayout: '#f0eee6',
    colorBgContainer: '#faf9f5',
    colorBgElevated: '#faf9f5',
    colorBorder: '#d8d5cc',
    colorBorderSecondary: '#e8e6dc',
    colorError: '#c0453a',
    borderRadius: 8,
    fontFamily: "'AnthropicSans Text', 'PingFang SC', 'Microsoft YaHei UI', 'Microsoft YaHei', sans-serif"
  }
}
</script>

<style>
</style>
