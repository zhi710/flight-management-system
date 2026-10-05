import { createRouter, createWebHistory } from 'vue-router'
import NProgress from 'nprogress'
import 'nprogress/nprogress.css'
import { useUserStore } from '@/stores/user'

NProgress.configure({ showSpinner: false })

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  scrollBehavior: () => ({ top: 0 }),
  routes: [
    {
      path: '/',
      redirect: '/login',
    },
    {
      path: '/home',
      component: () => import('@/layouts/MainLayout.vue'),
      meta: { requiresAuth: true },
      children: [
        {
          path: '',
          name: 'Home',
          component: () => import('@/views/Home.vue'),
          meta: { title: '首页', requiresAuth: true },
        },
        {
          path: 'flights',
          name: 'FlightSearch',
          component: () => import('@/views/flight/Search.vue'),
          meta: { title: '航班搜索', requiresAuth: true },
        },
        {
          path: 'flights/:id',
          name: 'FlightDetail',
          component: () => import('@/views/flight/Detail.vue'),
          meta: { title: '航班详情', requiresAuth: true },
        },
        {
          path: 'booking',
          name: 'Booking',
          component: () => import('@/views/order/Booking.vue'),
          meta: { title: '填写订单', requiresAuth: true },
        },
        {
          path: 'orders',
          name: 'OrderList',
          component: () => import('@/views/order/List.vue'),
          meta: { title: '我的订单', requiresAuth: true },
        },
        {
          path: 'orders/:id',
          name: 'OrderDetail',
          component: () => import('@/views/order/Detail.vue'),
          meta: { title: '订单详情', requiresAuth: true },
        },
        {
          // 支付成功后的落地页。原先支付完成直接跳订单详情，
          // 用户只看到一句 toast 就换了页面，缺一个"付款已成功"的确认节点。
          path: 'orders/:id/success',
          name: 'OrderSuccess',
          component: () => import('@/views/order/Success.vue'),
          meta: { title: '支付成功', requiresAuth: true },
        },
        {
          path: 'payment/:orderId',
          name: 'Payment',
          component: () => import('@/views/order/Payment.vue'),
          meta: { title: '支付', requiresAuth: true },
        },
        {
          path: 'checkin',
          name: 'Checkin',
          component: () => import('@/views/checkin/Index.vue'),
          meta: { title: '在线值机', requiresAuth: true },
        },
        {
          path: 'checkin/seat-map/:flightId',
          name: 'SeatMap',
          component: () => import('@/views/checkin/SeatMap.vue'),
          meta: { title: '选座', requiresAuth: true },
        },
        {
          path: 'checkin/boarding-pass/:checkinId',
          name: 'BoardingPass',
          component: () => import('@/views/checkin/BoardingPass.vue'),
          meta: { title: '电子登机牌', requiresAuth: true },
        },
        {
          path: 'flight-status',
          name: 'FlightStatus',
          component: () => import('@/views/status/Index.vue'),
          meta: { title: '航班动态', requiresAuth: true },
        },
        {
          path: 'member',
          name: 'MemberProfile',
          component: () => import('@/views/member/Profile.vue'),
          meta: { title: '个人中心', requiresAuth: true },
        },
        {
          path: 'member/miles',
          name: 'MemberMiles',
          component: () => import('@/views/member/Miles.vue'),
          meta: { title: '我的里程', requiresAuth: true },
        },
        {
          path: 'member/travelers',
          name: 'MemberTravelers',
          component: () => import('@/views/member/Travelers.vue'),
          meta: { title: '常用旅客', requiresAuth: true },
        },
        {
          path: 'help',
          name: 'Help',
          component: () => import('@/views/help/Index.vue'),
          meta: { title: '帮助中心', requiresAuth: true },
        },
        {
          path: 'notifications',
          name: 'Notifications',
          component: () => import('@/views/notification/Index.vue'),
          meta: { title: '通知中心', requiresAuth: true },
        },
        {
          path: 'subscriptions',
          name: 'Subscriptions',
          component: () => import('@/views/subscription/Index.vue'),
          meta: { title: '我的订阅', requiresAuth: true },
        },
        {
          path: 'feedback',
          name: 'Feedback',
          component: () => import('@/views/feedback/Index.vue'),
          meta: { title: '我的反馈', requiresAuth: true },
        },
        {
          path: 'orders/change',
          name: 'OrderChangeList',
          component: () => import('@/views/order/ChangeList.vue'),
          meta: { title: '改签退票', requiresAuth: true },
        },
        {
          // 已登录区内的兜底：保留顶部导航与页脚，用户能直接点回其他页面，
          // 而不是被静默弹回首页（原先 /home/xxx 会被弹回 /home，用户不知道为什么）
          path: ':pathMatch(.*)*',
          name: 'NotFoundInApp',
          component: () => import('@/views/NotFound.vue'),
          meta: { title: '页面不存在', requiresAuth: true },
        },
      ],
    },
    {
      path: '/login',
      name: 'Login',
      component: () => import('@/views/auth/Login.vue'),
      meta: { title: '登录' },
    },
    {
      path: '/register',
      name: 'Register',
      component: () => import('@/views/auth/Register.vue'),
      meta: { title: '注册' },
    },
    {
      // 注册页「服务条款 / 隐私政策」的落地页。
      // 原先这两个链接是 href="javascript:void(0)"，点了没反应，
      // 而用户被要求「我已阅读并同意」—— 看不到内容却要同意，属合规硬伤。
      path: '/terms',
      name: 'Terms',
      component: () => import('@/views/legal/Legal.vue'),
      meta: { title: '服务条款', doc: 'terms' },
    },
    {
      path: '/privacy',
      name: 'Privacy',
      component: () => import('@/views/legal/Legal.vue'),
      meta: { title: '隐私政策', doc: 'privacy' },
    },
    {
      // 登录区之外的兜底（如 /typo）。不再 redirect 到 /login ——
      // 那会让用户以为"登录过期了"，而实际只是地址写错。
      path: '/:pathMatch(.*)*',
      name: 'NotFound',
      component: () => import('@/views/NotFound.vue'),
      meta: { title: '页面不存在' },
    },
  ],
})

// Navigation guards
router.beforeEach(async (to, _from, next) => {
  NProgress.start()
  document.title = `${to.meta.title || 'SkyTrip'} - 天行航空`

  const userStore = useUserStore()

  // 如果用户已登录，访问登录/注册页面时重定向到首页
  if (userStore.isLoggedIn && (to.path === '/login' || to.path === '/register')) {
    next({ path: '/home' })
    return
  }

  // 如果路由需要认证但用户未登录，重定向到登录页面
  if (to.meta.requiresAuth && !userStore.isLoggedIn) {
    next({ path: '/login', query: { redirect: to.fullPath } })
    return
  }

  // 如果已登录但用户信息为空，重新获取用户信息
  if (userStore.isLoggedIn && !userStore.userInfo) {
    await userStore.fetchProfile()
    // 拉取资料时若 token 已失效（401 已在拦截器里登出），直接回登录页并带上回跳地址，
    // 不要继续放行到受保护页面，否则会出现「已登出仍进入首页」的闪烁
    if (!userStore.isLoggedIn) {
      next({ path: '/login', query: { redirect: to.fullPath } })
      return
    }
  }

  next()
})

router.afterEach(() => {
  NProgress.done()
})

export default router
