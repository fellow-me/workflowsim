<template>
  <div id="app">
    <header>
      <nav>
        <router-link to="/task-dependency-graph">任务依赖图</router-link>
        <router-link to="/gantt-chart">甘特图</router-link>
      </nav>
    </header>
    <main>
      <router-view/>
    </main>
  </div>
</template>

<style>
:root {
  --primary-color: #4a90e2;
  --primary-hover: #3a7bc8;
  --danger-color: #ea4335;
  --danger-hover: #d33426;
  --text-color: #2c3e50;
  --light-gray: #f5f5f5;
  --border-color: #e0e0e0;
  --shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  --border-radius: 8px;
}

* {
  box-sizing: border-box;
}

body {
  margin: 0;
  font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
  background-color: #f9f9f9;
}

#app {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  height: 100vh; /* 新增 */
  overflow: hidden; /* 新增 */
}

header {
  background-color: white;
  box-shadow: var(--shadow);
  padding: 0.4rem 0;
  position: sticky;
  top: 0;
  z-index: 100;
}

header h1 {
  margin: 0;
  color: var(--text-color);
  font-size: 1.8rem;
}

nav {
  display: flex;
  justify-content: center;
  gap: 2rem;
  padding: 1rem 0;
}

nav a {
  font-weight: 600;
  color: #666666;
  text-decoration: none;
  padding: 0.5rem 1rem;
  border-radius: var(--border-radius);
  transition: all 0.2s ease;
}

nav a:hover {
  color: var(--primary-color);
}

nav a.router-link-exact-active {
  color: var(--primary-color);
}

main {
  flex: 1;
  padding: 1rem; /* 减少内边距 */
  width: 100%;
  max-width: none; /* 移除最大宽度限制 */
  margin: 0;
  overflow: hidden; /* 新增 */
}

/* 统一按钮样式 */
button {
  padding: 0.5rem 1rem;
  background-color: var(--primary-color);
  color: white;
  border: none;
  border-radius: var(--border-radius);
  cursor: pointer;
  font-weight: 500;
  transition: all 0.2s ease;
  box-shadow: var(--shadow);
}

button:hover {
  background-color: var(--primary-hover);
  transform: translateY(-1px);
}

button:active {
  transform: translateY(0);
}

button.danger {
  background-color: var(--danger-color);
}

button.danger:hover {
  background-color: var(--danger-hover);
}

/* 统一文件上传样式 */
.file-input {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.file-input label {
  padding: 0.5rem 1rem;
  background-color: var(--primary-color);
  color: white;
  border-radius: var(--border-radius);
  cursor: pointer;
  transition: all 0.2s ease;
}

.file-input label:hover {
  background-color: var(--primary-hover);
}

.file-input input[type="file"] {
  display: none;
}

.file-name {
  font-size: 0.9rem;
  color: #666;
  margin-left: 0.5rem;
}

.buttons {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
}

/* 统一图表容器样式 */
.chart-container {
  background-color: white;
  border-radius: var(--border-radius);
  box-shadow: var(--shadow);
  overflow: hidden;
  margin-top: 1rem;
}
</style>
<script setup lang="ts">
</script>
