import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getToken } from '@/utils/auth'
import { useAuthStore } from '@/stores/auth'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/Login.vue'),
    meta: { title: '登录', public: true },
  },
  {
    path: '/',
    component: () => import('@/components/layout/AppLayout.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/Dashboard.vue'),
        meta: { title: '监控大屏', icon: 'Odometer' },
      },
      // ---- 航班调度 ----
      {
        path: 'flights',
        name: 'FlightList',
        component: () => import('@/views/flights/FlightList.vue'),
        meta: { title: '航班计划', icon: 'Promotion', parent: '航班调度', permission: 'flight:read' },
      },
      {
        path: 'flights/schedule',
        name: 'FlightSchedule',
        component: () => import('@/views/flights/FlightSchedule.vue'),
        meta: { title: '时刻表', icon: 'Calendar', parent: '航班调度', permission: 'flight:read' },
      },
      {
        path: 'flights/logs',
        name: 'FlightLog',
        component: () => import('@/views/flights/FlightLog.vue'),
        meta: { title: '航班日志', icon: 'Document', parent: '航班调度', permission: 'flight:read' },
      },
      // ---- 旅客服务 ----
      {
        path: 'passengers',
        name: 'PassengerList',
        component: () => import('@/views/passengers/PassengerList.vue'),
        meta: { title: '旅客查询', icon: 'User', parent: '旅客服务', permission: 'passenger:read' },
      },
      {
        path: 'members',
        name: 'MemberManage',
        component: () => import('@/views/system/MemberManage.vue'),
        meta: { title: '前台用户', icon: 'UserFilled', parent: '旅客服务', permission: 'passenger:read' },
      },
      {
        path: 'checkin/:flightId?',
        name: 'CheckinManage',
        component: () => import('@/views/passengers/CheckinManage.vue'),
        meta: { title: '值机管理', icon: 'Tickets', parent: '旅客服务', permission: 'passenger:write' },
      },
      {
        path: 'gates',
        name: 'GateManage',
        component: () => import('@/views/passengers/GateManage.vue'),
        meta: { title: '登机口管理', icon: 'Guide', parent: '旅客服务', permission: 'passenger:write' },
      },
      {
        path: 'seats',
        name: 'SeatManage',
        component: () => import('@/views/passengers/SeatManage.vue'),
        meta: { title: '座位管理', icon: 'Grid', parent: '旅客服务', permission: 'passenger:write' },
      },
      {
        path: 'ssr',
        name: 'SsrManage',
        component: () => import('@/views/passengers/SsrManage.vue'),
        meta: { title: '特殊服务(SSR)', icon: 'Service', parent: '旅客服务', permission: 'passenger:write' },
      },
      {
        path: 'feedback',
        name: 'FeedbackManage',
        component: () => import('@/views/feedback/FeedbackManage.vue'),
        meta: { title: '投诉建议', icon: 'ChatDotRound', parent: '旅客服务', permission: 'feedback:read' },
      },
      // ---- 机组管理 ----
      {
        path: 'crew/list',
        name: 'CrewManage',
        component: () => import('@/views/crew/CrewManage.vue'),
        meta: { title: '机组人员', icon: 'UserFilled', parent: '机组管理', permission: 'crew:write' },
      },
      {
        path: 'crew/schedule',
        name: 'CrewSchedule',
        component: () => import('@/views/crew/CrewSchedule.vue'),
        meta: { title: '机组排班', icon: 'Calendar', parent: '机组管理', permission: 'crew:write' },
      },
      {
        path: 'crew/dynamics',
        name: 'CrewDynamics',
        component: () => import('@/views/crew/CrewDynamics.vue'),
        meta: { title: '机组动态', icon: 'Position', parent: '机组管理', permission: 'crew:read' },
      },
      {
        path: 'crew/compliance',
        name: 'CrewCompliance',
        component: () => import('@/views/crew/CrewCompliance.vue'),
        meta: { title: '机组合规', icon: 'Finished', parent: '机组管理', permission: 'crew:read' },
      },
      {
        path: 'crew/qualifications',
        name: 'CrewQualifications',
        component: () => import('@/views/crew/CrewQualifications.vue'),
        meta: { title: '机组资质', icon: 'Postcard', parent: '机组管理', permission: 'crew:read' },
      },
      // ---- 客票管理 ----
      {
        path: 'tickets/bookings',
        name: 'BookingList',
        component: () => import('@/views/tickets/BookingList.vue'),
        meta: { title: '订座管理', icon: 'Document', parent: '客票管理', permission: 'ticket:read' },
      },
      {
        path: 'tickets/fares',
        name: 'FareManage',
        component: () => import('@/views/tickets/FareManage.vue'),
        meta: { title: '票价管理', icon: 'Money', parent: '客票管理', permission: 'ticket:write' },
      },
      {
        path: 'tickets/refunds',
        name: 'RefundManage',
        component: () => import('@/views/tickets/RefundManage.vue'),
        meta: { title: '退改管理', icon: 'RefreshRight', parent: '客票管理', permission: 'ticket:read' },
      },
      // ---- 运营监控 ----
      {
        path: 'alerts',
        name: 'AlertCenter',
        component: () => import('@/views/monitor/AlertCenter.vue'),
        meta: { title: '告警中心', icon: 'Bell', parent: '运营监控', permission: 'monitor:read' },
      },
      {
        path: 'irop',
        name: 'IropManage',
        component: () => import('@/views/monitor/IropManage.vue'),
        meta: { title: '不正常航班', icon: 'WarningFilled', parent: '运营监控', permission: 'monitor:write' },
      },
      {
        path: 'ground',
        name: 'GroundManage',
        component: () => import('@/views/monitor/GroundManage.vue'),
        meta: { title: '地面保障', icon: 'Scooter', parent: '运营监控', permission: 'monitor:write' },
      },
      {
        path: 'statistics',
        name: 'Statistics',
        component: () => import('@/views/monitor/Statistics.vue'),
        meta: { title: '统计概览', icon: 'DataAnalysis', parent: '运营监控', permission: 'monitor:read' },
      },
      {
        path: 'flight-map',
        name: 'FlightMap',
        component: () => import('@/views/monitor/FlightMap.vue'),
        meta: { title: '航班动态地图', icon: 'MapLocation', parent: '运营监控', permission: 'monitor:read' },
      },
      // ---- 报表中心 ----
      {
        path: 'reports/operation',
        name: 'OperationReport',
        component: () => import('@/views/reports/OperationReport.vue'),
        meta: { title: '运营报表', icon: 'Histogram', parent: '报表中心', permission: 'report:read' },
      },
      {
        path: 'reports/revenue',
        name: 'RevenueReport',
        component: () => import('@/views/reports/RevenueReport.vue'),
        meta: { title: '收入报表', icon: 'TrendCharts', parent: '报表中心', permission: 'report:read' },
      },
      {
        path: 'reports/custom',
        name: 'CustomReport',
        component: () => import('@/views/reports/CustomReport.vue'),
        meta: { title: '自定义报表', icon: 'DocumentCopy', parent: '报表中心', permission: 'report:read' },
      },
      // ---- 系统管理 ----
      {
        path: 'system/users',
        name: 'UserManage',
        component: () => import('@/views/system/UserManage.vue'),
        meta: { title: '用户管理', icon: 'Avatar', parent: '系统管理', permission: 'system:user' },
      },
      {
        path: 'system/roles',
        name: 'RoleManage',
        component: () => import('@/views/system/RoleManage.vue'),
        meta: { title: '角色权限', icon: 'Lock', parent: '系统管理', permission: 'system:role' },
      },
      {
        path: 'system/master-data',
        name: 'MasterData',
        component: () => import('@/views/system/MasterData.vue'),
        meta: { title: '基础数据', icon: 'Folder', parent: '系统管理', permission: 'system:data' },
      },
      {
        path: 'system/logs',
        name: 'OperationLogs',
        component: () => import('@/views/system/OperationLogs.vue'),
        meta: { title: '操作日志', icon: 'Notebook', parent: '系统管理', permission: 'system:user' },
      },
      {
        path: 'system/config',
        name: 'SystemConfig',
        component: () => import('@/views/system/SystemConfig.vue'),
        meta: { title: '系统配置', icon: 'Setting', parent: '系统管理', permission: 'system:config' },
      },
      {
        // 兜底放在 layout 的 children 里：这样输错地址时侧边栏仍在，
        // 用户可以直接点回任一处，而不是被静默丢到监控大屏（原行为）
        path: ':pathMatch(.*)*',
        name: 'NotFound',
        component: () => import('@/views/NotFound.vue'),
        meta: { title: '页面不存在' },
      },
    ],
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

// 路由守卫
router.beforeEach((to, _from, next) => {
  document.title = `${to.meta.title || 'SkyOps'} - 航班管理系统`

  if (to.meta.public) {
    next()
    return
  }

  const token = getToken()
  if (!token) {
    next({ path: '/login', query: { redirect: to.fullPath } })
    return
  }

  // 按权限码拦截路由（无权限则退回监控大屏）
  const required = to.meta.permission as string | undefined
  if (required) {
    const authStore = useAuthStore()
    if (!authStore.hasPermission(required)) {
      ElMessage.error('无权限访问该页面')
      next({ path: '/dashboard', replace: true })
      return
    }
  }

  next()
})

export default router
