<template>
  <header class="app-header">
    <div class="header-inner">
      <!-- 移动端菜单按钮（≤1024px 时替代桌面导航） -->
      <button
        class="nav-toggle"
        type="button"
        :aria-label="$t('header.menu')"
        :aria-expanded="mobileNavOpen ? 'true' : 'false'"
        @click="mobileNavOpen = true"
      >
        <el-icon :size="22"><Menu /></el-icon>
      </button>

      <!-- Logo -->
      <router-link to="/home" class="logo">
        <el-icon :size="28" color="var(--color-primary)"><Promotion /></el-icon>
        <span class="logo-text">SkyTrip</span>
      </router-link>

      <!-- 桌面导航 -->
      <nav class="nav-menu">
        <router-link
          v-for="item in navItems"
          :key="item.to"
          :to="item.to"
          class="nav-item"
          :class="{ active: isActive(item) }"
        >
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ $t(item.key) }}</span>
        </router-link>
      </nav>

      <!-- Notification Bell -->
      <NotificationBell />

      <!-- User Area -->
      <div class="user-area">
        <!-- Language Switcher -->
        <el-dropdown trigger="click" @command="changeLanguage" class="language-switcher">
          <div class="lang-btn">
            <el-icon :size="18"><Connection /></el-icon>
            <span class="lang-label">{{ currentLangIcon }}</span>
          </div>
          <template #dropdown>
            <el-dropdown-menu class="lang-menu">
              <el-dropdown-item
                v-for="lang in localeOptions"
                :key="lang.value"
                :command="lang.value"
              >
                <span class="lang-opt" :class="{ 'is-active': currentLocale === lang.value }">
                  <span class="lang-opt__code">{{ lang.icon }}</span>
                  <span class="lang-opt__text">
                    <span class="lang-opt__name">{{ lang.label }}</span>
                    <span class="lang-opt__region">{{ lang.region }}</span>
                  </span>
                  <el-icon v-if="currentLocale === lang.value" class="lang-opt__check"><Select /></el-icon>
                </span>
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>

        <el-dropdown trigger="click" @command="handleCommand">
          <div class="user-info">
            <el-avatar :size="32" :src="getAvatarUrl(userStore.userInfo?.avatar)" icon="UserFilled" />
            <span class="user-name">{{ userStore.userInfo?.name || $t('header.passenger') }}</span>
            <el-icon><ArrowDown /></el-icon>
          </div>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="orders">
                <el-icon><List /></el-icon> {{ $t('header.orders') }}
              </el-dropdown-item>
              <el-dropdown-item command="member">
                <el-icon><User /></el-icon> {{ $t('header.profile') }}
              </el-dropdown-item>
              <el-dropdown-item command="miles">
                <el-icon><Medal /></el-icon> {{ $t('header.miles') }}
              </el-dropdown-item>
              <el-dropdown-item command="travelers">
                <el-icon><UserFilled /></el-icon> {{ $t('header.travelers') }}
              </el-dropdown-item>
              <el-dropdown-item command="notifications">
                <el-icon><Bell /></el-icon> {{ $t('header.notifications') }}
              </el-dropdown-item>
              <el-dropdown-item command="subscriptions">
                <el-icon><Star /></el-icon> {{ $t('header.subscriptions') }}
              </el-dropdown-item>
              <el-dropdown-item command="feedback">
                <el-icon><ChatDotRound /></el-icon> {{ $t('header.feedback') }}
              </el-dropdown-item>
              <el-dropdown-item command="changeRecords">
                <el-icon><RefreshRight /></el-icon> {{ $t('header.changeRecords') }}
              </el-dropdown-item>
              <el-dropdown-item divided command="logout">
                <el-icon><SwitchButton /></el-icon> {{ $t('header.logout') }}
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </div>

    <!-- 移动端导航抽屉：导航项与语言切换都收在这里，窄屏也能切语言 -->
    <el-drawer
      v-model="mobileNavOpen"
      direction="ltr"
      size="288px"
      :with-header="false"
      class="mobile-nav-drawer"
    >
      <div class="mobile-nav">
        <div class="mobile-nav__head">
          <el-icon :size="24" color="var(--color-primary)"><Promotion /></el-icon>
          <span class="mobile-nav__brand">SkyTrip</span>
        </div>

        <nav class="mobile-nav__list">
          <router-link
            v-for="item in navItems"
            :key="item.to"
            :to="item.to"
            class="mobile-nav__item"
            :class="{ active: isActive(item) }"
            @click="mobileNavOpen = false"
          >
            <el-icon :size="18"><component :is="item.icon" /></el-icon>
            <span>{{ $t(item.key) }}</span>
          </router-link>
        </nav>

        <div class="mobile-nav__lang">
          <div class="mobile-nav__section">{{ $t('header.language') }}</div>
          <div class="mobile-lang-grid">
            <button
              v-for="lang in localeOptions"
              :key="lang.value"
              type="button"
              class="mobile-lang-btn"
              :class="{ 'is-active': currentLocale === lang.value }"
              @click="changeLanguage(lang.value)"
            >
              <span class="mobile-lang-btn__code">{{ lang.icon }}</span>
              <span class="mobile-lang-btn__name">{{ lang.label }}</span>
            </button>
          </div>
        </div>
      </div>
    </el-drawer>
  </header>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useUserStore } from '@/stores/user'
import { getAvatarUrl } from '@/utils/image'
import { localeOptions, getLocaleIcon } from '@/locales'
import NotificationBell from '@/components/NotificationBell.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const { locale } = useI18n()

const currentLocale = computed(() => locale.value)
const currentLangIcon = computed(() => getLocaleIcon(locale.value))

/** 移动端抽屉开关 */
const mobileNavOpen = ref(false)

/**
 * 导航项唯一真源：桌面横向导航与移动端抽屉共用，
 * 避免"加了页面只改了一处"这种典型漏改。
 */
interface NavItem {
  to: string
  icon: string
  key: string
  /** true = 用前缀匹配高亮（详情页也算在父级） */
  prefix?: boolean
}

const navItems: NavItem[] = [
  { to: '/home', icon: 'HomeFilled', key: 'header.home' },
  { to: '/home/flights', icon: 'Search', key: 'header.flights', prefix: true },
  { to: '/home/flight-status', icon: 'Position', key: 'header.flightStatus' },
  { to: '/home/checkin', icon: 'Ticket', key: 'header.checkin', prefix: true },
  { to: '/home/help', icon: 'QuestionFilled', key: 'header.help' },
]

function isActive(item: NavItem): boolean {
  return item.prefix ? route.path.startsWith(item.to) : route.path === item.to
}

function changeLanguage(lang: string) {
  if (lang === locale.value) {
    mobileNavOpen.value = false
    return
  }
  locale.value = lang
  localStorage.setItem('locale', lang)
  // Element Plus 组件文案由 App.vue 的 el-config-provider 响应式接管，
  // 因此这里不需要 window.location.reload()
  mobileNavOpen.value = false
}

function handleCommand(cmd: string) {
  mobileNavOpen.value = false
  switch (cmd) {
    case 'orders':
      router.push('/home/orders')
      break
    case 'member':
      router.push('/home/member')
      break
    case 'miles':
      router.push('/home/member/miles')
      break
    case 'travelers':
      router.push('/home/member/travelers')
      break
    case 'notifications':
      router.push('/home/notifications')
      break
    case 'subscriptions':
      router.push('/home/subscriptions')
      break
    case 'feedback':
      router.push('/home/feedback')
      break
    case 'changeRecords':
      router.push('/home/orders/change')
      break
    case 'logout':
      userStore.logout()
      router.push('/login')
      break
  }
}
</script>

<style scoped>
.app-header {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 1000;
  height: var(--header-height);
  background: var(--bg-header);
  box-shadow: var(--shadow-sm);
}

.header-inner {
  max-width: 1400px;
  margin: 0 auto;
  height: 100%;
  display: flex;
  align-items: center;
  padding: 0 24px;
  gap: 24px;
}

/* 汉堡按钮：桌面端隐藏 */
.nav-toggle {
  display: none;
  flex: none;
  align-items: center;
  justify-content: center;
  width: var(--control-height);
  height: var(--control-height);
  padding: 0;
  border: none;
  border-radius: var(--radius-control);
  background: transparent;
  color: var(--text-regular);
  cursor: pointer;
  transition: background var(--duration-fast, 0.2s);
}

.nav-toggle:hover {
  background: var(--bg-muted);
  color: var(--color-primary);
}

.nav-toggle:focus-visible {
  outline: none;
  box-shadow: var(--shadow-focus);
}

.logo {
  display: flex;
  align-items: center;
  gap: 8px;
  text-decoration: none;
  flex-shrink: 0;
}

.logo-text {
  font-size: 22px;
  font-weight: 700;
  color: var(--color-primary);
  letter-spacing: 1px;
}

.logo-text small {
  font-size: 12px;
  font-weight: 400;
  color: var(--text-secondary);
  margin-left: 4px;
}

.nav-menu {
  display: flex;
  align-items: center;
  gap: 4px;
  flex: 1;
  min-width: 0;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 8px 14px;
  border-radius: 8px;
  color: var(--text-regular);
  font-size: 14px;
  text-decoration: none;
  transition: all 0.2s;
  /* 关键：不加这两行，窄屏下 flex 会把中文逐字压成竖排（实测「机票搜索」被压成 5 行） */
  white-space: nowrap;
  flex: none;
}

.nav-item:hover,
.nav-item.active {
  color: var(--color-primary);
  background: var(--color-primary-soft);
}

.user-area {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 12px;
}

.language-switcher {
  cursor: pointer;
}

.lang-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 10px;
  border-radius: 6px;
  color: var(--text-regular);
  font-size: 14px;
  transition: all 0.2s;
}

.lang-btn:hover {
  background: var(--bg-muted);
  color: var(--color-primary);
}

.lang-label {
  font-weight: 600;
  font-size: 13px;
}

/* 语言下拉：6 种语言要一眼分得清 —— 用母语自称 + 地区小字，当前项打勾 */
.lang-menu {
  min-width: 200px;
}

.lang-opt {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  line-height: 1.25;
}

.lang-opt__code {
  flex: none;
  width: 26px;
  height: 22px;
  display: grid;
  place-items: center;
  border-radius: var(--radius-sm);
  background: var(--bg-muted);
  color: var(--text-secondary);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.02em;
}

.lang-opt__text {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.lang-opt__name {
  color: var(--text-primary);
  font-size: 13px;
}

.lang-opt__region {
  color: var(--text-secondary);
  font-size: 11px;
}

.lang-opt__check {
  margin-left: auto;
  color: var(--color-primary);
}

.lang-opt.is-active .lang-opt__code {
  background: var(--color-primary-soft);
  color: var(--color-primary);
}

.lang-opt.is-active .lang-opt__name {
  color: var(--color-primary);
  font-weight: 600;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 8px;
  transition: background 0.2s;
}

.user-info:hover {
  background: var(--bg-muted);
}

.user-name {
  font-size: 14px;
  color: var(--text-primary);
  max-width: 80px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ===== 移动端抽屉 ===== */
.mobile-nav {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: 4px;
}

.mobile-nav__head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 8px 20px;
}

.mobile-nav__brand {
  font-size: 20px;
  font-weight: 700;
  color: var(--color-primary);
  letter-spacing: 1px;
}

.mobile-nav__list {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.mobile-nav__item {
  display: flex;
  align-items: center;
  gap: 10px;
  /* 抽屉里的导航项也是点击目标：给足 44px 高（移动端触控下限） */
  min-height: 44px;
  padding: 0 12px;
  border-radius: var(--radius-control);
  color: var(--text-regular);
  font-size: 15px;
  text-decoration: none;
  transition: background 0.2s;
}

.mobile-nav__item:hover,
.mobile-nav__item.active {
  color: var(--color-primary);
  background: var(--color-primary-soft);
}

.mobile-nav__item.active {
  font-weight: 600;
}

.mobile-nav__lang {
  margin-top: auto;
  padding-top: 20px;
}

.mobile-nav__section {
  padding: 0 8px 10px;
  color: var(--text-secondary);
  font-size: 12px;
}

.mobile-lang-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}

.mobile-lang-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 44px;
  padding: 0 10px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-control);
  background: var(--bg-card);
  color: var(--text-primary);
  font-size: 13px;
  text-align: left;
  cursor: pointer;
  transition: border-color 0.2s, background 0.2s;
}

.mobile-lang-btn__code {
  flex: none;
  width: 24px;
  height: 20px;
  display: grid;
  place-items: center;
  border-radius: var(--radius-sm);
  background: var(--bg-muted);
  color: var(--text-secondary);
  font-size: 10px;
  font-weight: 600;
}

.mobile-lang-btn__name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.mobile-lang-btn:hover {
  border-color: var(--color-primary-border);
}

.mobile-lang-btn.is-active {
  border-color: var(--color-primary);
  background: var(--color-primary-soft);
  color: var(--color-primary);
  font-weight: 600;
}

.mobile-lang-btn.is-active .mobile-lang-btn__code {
  background: var(--color-primary);
  color: var(--text-inverse);
}

/**
 * 断点取 1024px：低于此宽时横向导航 + 语言按钮 + 用户区会互相挤压。
 * 用汉堡抽屉替代，而不是继续压缩 —— 压缩的代价是中文被逐字换行，反而更糟。
 */
@media (max-width: 1024px) {
  .header-inner {
    padding: 0 16px;
    gap: 12px;
  }

  .nav-toggle {
    display: flex;
  }

  .nav-menu,
  .language-switcher {
    display: none;
  }

  /* 移动端 logo 让位给汉堡按钮，字号略收 */
  .logo-text {
    font-size: 19px;
  }
}

@media (max-width: 480px) {
  .header-inner {
    padding: 0 12px;
    gap: 8px;
  }

  .logo-text {
    font-size: 17px;
  }
}
</style>
