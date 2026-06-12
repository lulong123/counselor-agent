<template>
  <router-view />
</template>

<style>
/* ── Global Reset & CSS Variables ── */
*,
*::before,
*::after {
  box-sizing: border-box;
}

:root {
  /* Background — warm */
  --bg-app: #f5f0e8;
  --bg-surface: #fffdf7;
  --bg-sidebar: #fefcf5;
  --bg-sidebar-hover: #faf3e0;
  --bg-sidebar-active: #f5e6c8;

  /* Text */
  --text-primary: #4a3f2f;
  --text-secondary: #7a6e5b;
  --text-tertiary: #9b8e7a;
  --text-sidebar: #4a3f2f;
  --text-sidebar-active: #4a3f2f;
  --text-sidebar-dim: #9b8e7a;

  /* Borders */
  --border-default: #e8dcc8;
  --border-subtle: #efe8d8;

  /* Accent — warm gold */
  --accent: #c8881a;
  --accent-hover: #9a6510;
  --accent-soft: rgba(200, 136, 26, 0.08);
  --accent-light: rgba(200, 136, 26, 0.15);

  /* Radii */
  --radius-sm: 6px;
  --radius-md: 10px;
  --radius-lg: 14px;
  --radius-xl: 18px;

  /* Shadows */
  --shadow-xs: 0 1px 2px rgba(154, 101, 16, 0.04);
  --shadow-sm: 0 1px 3px rgba(154, 101, 16, 0.06);
  --shadow-md: 0 4px 16px rgba(154, 101, 16, 0.08);
  --shadow-lg: 0 12px 32px rgba(154, 101, 16, 0.1);
  --shadow-card: 0 1px 3px rgba(154, 101, 16, 0.05);

  /* Transitions */
  --ease-out: cubic-bezier(0.16, 1, 0.3, 1);
  --ease-in-out: cubic-bezier(0.65, 0, 0.35, 1);
}

body {
  margin: 0;
  font-family: "PingFang SC", -apple-system, BlinkMacSystemFont, "Segoe UI", system-ui, sans-serif;
  font-size: 14px;
  color: var(--text-primary);
  background: var(--bg-app);
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}

/* Scrollbar */
::-webkit-scrollbar {
  width: 5px;
  height: 5px;
}
::-webkit-scrollbar-track {
  background: transparent;
}
::-webkit-scrollbar-thumb {
  background: #d1d5db;
  border-radius: 10px;
}
::-webkit-scrollbar-thumb:hover {
  background: #b0b7c3;
}

/* Element Plus overrides */
.el-button--primary {
  --el-button-bg-color: var(--accent);
  --el-button-border-color: var(--accent);
  --el-button-hover-bg-color: var(--accent-hover);
  --el-button-hover-border-color: var(--accent-hover);
}
</style>
