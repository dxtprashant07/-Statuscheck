import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  server: {
    // Bind to IPv4 loopback explicitly: this machine's IPv6 loopback (::1) is
    // unreachable, and Node resolves "localhost" to ::1 first, which breaks both
    // the browser connection and the proxy below.
    host: "127.0.0.1",
    port: 5173,
    proxy: {
      "/api": {
        target: "http://127.0.0.1:8080",
        changeOrigin: true
      }
    }
  }
});
