import { defineConfig } from "vite";
import { svelte } from "@sveltejs/vite-plugin-svelte";

// Due entry point serviti dall'host Android: /viewer (TV) e /spectator (telefono).
export default defineConfig({
  plugins: [svelte()],
  build: {
    rollupOptions: {
      input: {
        viewer: "viewer/index.html",
        spectator: "spectator/index.html",
      },
    },
  },
});
