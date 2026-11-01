import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// The Spring backend has no CORS config, so in dev we proxy /api/* -> :8080/*
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: process.env.VITE_BACKEND_URL || 'http://localhost:8080',
        changeOrigin: true,
        rewrite: (p) => p.replace(/^\/api/, ''),
      },
    },
  },
});
