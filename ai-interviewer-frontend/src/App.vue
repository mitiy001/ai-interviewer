<script setup lang="ts">
import { useAuthStore } from '@/stores/auth'
import { useRouter } from 'vue-router'

const auth = useAuthStore()
const router = useRouter()

async function handleLogout() {
  await auth.logout()
  router.push('/login')
}
</script>

<template>
  <div class="layout">
    <nav class="nav">
      <router-link to="/" class="nav-logo">
        <span class="nav-logo-mark">AI</span>
        <span class="nav-logo-text">AI 面试官</span>
      </router-link>
      <div class="nav-links">
        <template v-if="auth.user">
          <router-link to="/" class="nav-link">首页</router-link>
          <router-link to="/upload" class="nav-link">上传</router-link>
          <router-link to="/interview" class="nav-link">面试</router-link>
          <router-link to="/report" class="nav-link">报告</router-link>
          <router-link to="/settings" class="nav-link">设置</router-link>
          <router-link v-if="auth.isAdmin" to="/admin/users" class="nav-link nav-admin">用户管理</router-link>
          <router-link v-if="auth.isAdmin" to="/admin/skills" class="nav-link nav-admin">Skill 标准</router-link>
          <span class="nav-user">
            <span class="nav-username">{{ auth.user.username }}</span>
            <button class="nav-logout" @click="handleLogout">退出</button>
          </span>
        </template>
        <template v-else>
          <router-link to="/login" class="nav-link">登录</router-link>
        </template>
      </div>
    </nav>
    <main class="main">
      <router-view v-slot="{ Component }">
        <transition name="page" mode="out-in">
          <component :is="Component" />
        </transition>
      </router-view>
    </main>
  </div>
</template>

<style>
/* 页面切换过渡 */
.page-enter-active,
.page-leave-active {
  transition: opacity 0.22s var(--ease-out), transform 0.22s var(--ease-out);
}

.page-enter-from {
  opacity: 0;
  transform: translateY(8px);
}

.page-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}

.nav-user {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: 8px;
  padding-left: 12px;
  border-left: 1px solid #ddd;
}

.nav-username {
  font-size: 13px;
  color: #555;
  font-weight: 500;
}

.nav-logout {
  background: none;
  border: 1px solid #ddd;
  border-radius: 6px;
  padding: 4px 10px;
  font-size: 12px;
  color: #888;
  cursor: pointer;
  transition: all 0.2s;
}

.nav-logout:hover {
  border-color: #e74c3c;
  color: #e74c3c;
}
</style>