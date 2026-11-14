// Vite 开发服务器配置（只在开发调试时生效，不影响打包产物）。
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    // 同时监听 IPv4/IPv6（'::' 是双栈）。只绑 'localhost' 时 Vite 会落到 ::1，
    // 浏览器一旦把 localhost 解析成 127.0.0.1 就会连不上，表现为偶发 "Network Error"。
    host: '::',
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/uploads': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/ws': {
        target: 'ws://localhost:8080',
        ws: true,
      },
    },
  },
})
