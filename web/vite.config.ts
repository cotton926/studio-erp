import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

/**
 * 【为什么开发环境要把 /api 代理到 8080】
 * 后端没有开 CORS，前端若是直接请求 http://localhost:8080 会被浏览器拦下；
 * 就算后端开了 CORS，cookie / 自定义 header 也会带来一堆预检问题。
 * 用代理后，前端始终请求同源的 /api，浏览器根本不认为这是跨域请求，
 * 上线时换成 Nginx 反代同样的一段配置即可，前端代码一行都不用改。
 */
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true,
      },
    },
  },
  build: {
    // 内部系统不追求极致体积，但 2MB 的单包会让首次加载明显卡顿；
    // 拆出 echarts 与 element-plus 后，业务代码变动不会让这两个大包重新下载
    chunkSizeWarningLimit: 1500,
    rollupOptions: {
      output: {
        manualChunks: {
          echarts: ['echarts'],
          element: ['element-plus'],
        },
      },
    },
  },
})
