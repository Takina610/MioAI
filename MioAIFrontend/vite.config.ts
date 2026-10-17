import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

export default defineConfig({
  base: '/',
  plugins: [vue()],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src')
    }
  },
  build: {
    // 默认目标含需 -webkit- 前缀的旧 Safari，压缩时会把标准 backdrop-filter 改写成仅剩前缀版，
    // 而新版 Chromium 已不认该前缀别名 → 毛玻璃整体失效。本项目只跑现代浏览器，目标整体调新
    cssTarget: ['chrome109', 'edge109', 'firefox110', 'safari16.4']
  },
  server: {
    port: 3000,
    proxy: {
      // 与生产 nginx 一致：转发时剥掉 /api 前缀（后端控制器映射不带 /api）
      '/api': {
        target: 'http://127.0.0.1:8081',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, ''),
        // SSE 长连接：转发层不做超时（后端 15s 心跳保活；连接生死交给前端看门狗判定）
        timeout: 0,
        proxyTimeout: 0
      }
    }
  },
  css: {
    preprocessorOptions: {
      scss: {
        additionalData: `@import "@/styles/variables.scss";`,
        silenceDeprecations: ['import']
      }
    }
  }
})