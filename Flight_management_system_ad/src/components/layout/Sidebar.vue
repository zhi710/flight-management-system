<template>
  <div class="sidebar" :class="{ collapsed: appStore.sidebarCollapsed }">
    <!-- Logo -->
    <div class="sidebar-logo">
      <el-icon :size="28" color="var(--color-primary-on-dark)"><Promotion /></el-icon>
      <span v-show="!appStore.sidebarCollapsed" class="logo-text">SkyOps</span>
    </div>

    <!-- 导航菜单 -->
    <el-scrollbar class="sidebar-menu-wrap">
      <el-menu
        :default-active="activeMenu"
        :collapse="appStore.sidebarCollapsed"
        :collapse-transition="false"
        background-color="var(--bg-sidebar)"
        text-color="var(--text-on-dark-secondary)"
        active-text-color="var(--color-primary-on-dark)"
        router
      >
        <el-menu-item index="/dashboard">
          <el-icon><Odometer /></el-icon>
          <template #title>监控大屏</template>
        </el-menu-item>

        <el-sub-menu v-if="groupVisible.flight" index="flight-group">
          <template #title>
            <el-icon><Promotion /></el-icon>
            <span>航班调度</span>
          </template>
          <el-menu-item v-if="can('flight:read')" index="/flights">航班计划</el-menu-item>
          <el-menu-item v-if="can('flight:read')" index="/flights/schedule">时刻表</el-menu-item>
          <el-menu-item v-if="can('flight:read')" index="/flights/logs">航班日志</el-menu-item>
        </el-sub-menu>

        <el-sub-menu v-if="groupVisible.passenger" index="pax-group">
          <template #title>
            <el-icon><User /></el-icon>
            <span>旅客服务</span>
          </template>
          <el-menu-item v-if="can('passenger:read')" index="/passengers">旅客查询</el-menu-item>
          <el-menu-item v-if="can('passenger:read')" index="/members">前台用户</el-menu-item>
          <el-menu-item v-if="can('passenger:write')" index="/checkin">值机管理</el-menu-item>
          <el-menu-item v-if="can('passenger:write')" index="/gates">登机口管理</el-menu-item>
          <el-menu-item v-if="can('passenger:write')" index="/seats">座位管理</el-menu-item>
          <el-menu-item v-if="can('passenger:write')" index="/ssr">特殊服务(SSR)</el-menu-item>
          <el-menu-item v-if="can('feedback:read')" index="/feedback">投诉建议</el-menu-item>
        </el-sub-menu>

        <el-sub-menu v-if="groupVisible.crew" index="crew-group">
          <template #title>
            <el-icon><UserFilled /></el-icon>
            <span>机组管理</span>
          </template>
          <el-menu-item v-if="can('crew:write')" index="/crew/list">机组人员</el-menu-item>
          <el-menu-item v-if="can('crew:write')" index="/crew/schedule">机组排班</el-menu-item>
          <el-menu-item v-if="can('crew:read')" index="/crew/dynamics">机组动态</el-menu-item>
          <el-menu-item v-if="can('crew:read')" index="/crew/compliance">机组合规</el-menu-item>
          <el-menu-item v-if="can('crew:read')" index="/crew/qualifications">机组资质</el-menu-item>
        </el-sub-menu>

        <el-sub-menu v-if="groupVisible.ticket" index="ticket-group">
          <template #title>
            <el-icon><Ticket /></el-icon>
            <span>客票管理</span>
          </template>
          <el-menu-item v-if="can('ticket:read')" index="/tickets/bookings">订座管理</el-menu-item>
          <el-menu-item v-if="can('ticket:write')" index="/tickets/fares">票价管理</el-menu-item>
          <el-menu-item v-if="can('ticket:read')" index="/tickets/refunds">退改管理</el-menu-item>
        </el-sub-menu>

        <el-sub-menu v-if="groupVisible.monitor" index="monitor-group">
          <template #title>
            <el-icon><DataAnalysis /></el-icon>
            <span>运营监控</span>
          </template>
          <el-menu-item v-if="can('monitor:read')" index="/alerts">告警中心</el-menu-item>
          <el-menu-item v-if="can('monitor:write')" index="/irop">不正常航班</el-menu-item>
          <el-menu-item v-if="can('monitor:write')" index="/ground">地面保障</el-menu-item>
          <el-menu-item v-if="can('monitor:read')" index="/statistics">统计概览</el-menu-item>
          <el-menu-item v-if="can('monitor:read')" index="/flight-map">航班动态地图</el-menu-item>
        </el-sub-menu>

        <el-sub-menu v-if="groupVisible.report" index="report-group">
          <template #title>
            <el-icon><Histogram /></el-icon>
            <span>报表中心</span>
          </template>
          <el-menu-item v-if="can('report:read')" index="/reports/operation">运营报表</el-menu-item>
          <el-menu-item v-if="can('report:read')" index="/reports/revenue">收入报表</el-menu-item>
          <el-menu-item v-if="can('report:read')" index="/reports/custom">自定义报表</el-menu-item>
        </el-sub-menu>

        <el-sub-menu v-if="groupVisible.system" index="system-group">
          <template #title>
            <el-icon><Setting /></el-icon>
            <span>系统管理</span>
          </template>
          <el-menu-item v-if="can('system:user')" index="/system/users">用户管理</el-menu-item>
          <el-menu-item v-if="can('system:role')" index="/system/roles">角色权限</el-menu-item>
          <el-menu-item v-if="can('system:data')" index="/system/master-data">基础数据</el-menu-item>
          <el-menu-item v-if="can('system:user')" index="/system/logs">操作日志</el-menu-item>
          <el-menu-item v-if="can('system:config')" index="/system/config">系统配置</el-menu-item>
        </el-sub-menu>
      </el-menu>
    </el-scrollbar>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useAppStore } from '@/stores/app'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const appStore = useAppStore()
const authStore = useAuthStore()

const activeMenu = computed(() => route.path)

/** 是否有某权限码（超级管理员直接放行） */
function can(perm: string) {
  return authStore.hasPermission(perm)
}

/** 每个菜单分组是否至少有一个可见子项（无可见子项则整组隐藏） */
const groupVisible = computed(() => ({
  flight: can('flight:read'),
  passenger: can('passenger:read') || can('passenger:write') || can('feedback:read'),
  crew: can('crew:read') || can('crew:write'),
  ticket: can('ticket:read') || can('ticket:write'),
  monitor: can('monitor:read') || can('monitor:write'),
  report: can('report:read'),
  system: can('system:user') || can('system:role') || can('system:config') || can('system:data'),
}))
</script>

<style scoped lang="scss">

.sidebar {
  position: fixed;
  left: 0;
  top: 0;
  width: $sidebar-width;
  height: 100vh;
  background: $bg-sidebar;
  transition: width 0.3s ease;
  z-index: 1001;
  display: flex;
  flex-direction: column;
  overflow: hidden;

  &.collapsed {
    width: $sidebar-collapsed-width;
  }
}

.sidebar-logo {
  height: $header-height;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  flex-shrink: 0;
  padding: 0 16px;
}

.logo-text {
  font-size: 20px;
  font-weight: 700;
  color: var(--text-on-dark-strong);
  letter-spacing: 2px;
  white-space: nowrap;
}

.sidebar-menu-wrap {
  flex: 1;
  overflow: hidden;
}

// Element Plus 菜单样式覆盖
:deep(.el-menu) {
  border-right: none;

  .el-menu-item,
  .el-sub-menu__title {
    height: 48px;
    line-height: 48px;

    &:hover {
      background-color: $bg-sidebar-active !important;
    }
  }

  .el-menu-item.is-active {
    background-color: $bg-sidebar-active !important;
    border-right: 3px solid $color-primary;
  }

  .el-sub-menu .el-menu-item {
    padding-left: 56px !important;
    min-width: auto;
  }
}
</style>
