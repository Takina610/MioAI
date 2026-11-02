import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

// 独立于 vite.config.ts：测试不需要 dev server/代理，只复用别名与 Vue 插件
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src')
    }
  },
  test: {
    environment: 'jsdom',
    include: ['src/**/*.test.ts'],
    globals: true
  }
})
