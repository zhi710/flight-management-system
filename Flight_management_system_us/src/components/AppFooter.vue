<template>
  <footer class="app-footer">
    <div class="footer-inner">
      <div class="footer-brand">
        <div class="footer-logo">
          <el-icon :size="24" color="var(--color-primary)"><Promotion /></el-icon>
          <span>SkyTrip</span>
        </div>
        <p class="footer-slogan">{{ $t('footer.slogan') }}</p>
      </div>
      <div class="footer-links">
        <div class="link-group">
          <h4>{{ $t('footer.flightServices')  }}</h4>
          <router-link to="/home/flights">{{ $t('header.flights') }}</router-link>
          <router-link to="/home/flight-status">{{ $t('header.flightStatus') }}</router-link>
          <router-link to="/home/checkin">{{ $t('header.checkin') }}</router-link>
        </div>
        <div class="link-group">
          <h4>{{ $t('footer.orderManagement')  }}</h4>
          <router-link to="/home/orders">{{ $t('header.orders') }}</router-link>
          <router-link to="/home/member/travelers">{{ $t('header.travelers') }}</router-link>
          <router-link to="/home/member/miles">{{ $t('header.miles') }}</router-link>
        </div>
        <div class="link-group">
          <h4>{{ $t('footer.helpSupport')  }}</h4>
          <router-link to="/home/help">{{ $t('header.help') }}</router-link>
          <router-link to="/home/help">{{ $t('help.refundPolicy') }}</router-link>
          <router-link to="/home/help">{{ $t('help.baggageRules') }}</router-link>
        </div>
      </div>
      <div class="footer-bottom">
        <p>{{ $t('footer.copyright') }}</p>
      </div>
    </div>
  </footer>
</template>

<style scoped>
.app-footer {
  background: var(--bg-sidebar);
  color: var(--text-on-dark);
  margin-top: auto;
}

.footer-inner {
  max-width: 1200px;
  margin: 0 auto;
  padding: 48px 24px 24px;
}

.footer-brand {
  margin-bottom: 32px;
}

.footer-logo {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 20px;
  font-weight: 600;
  margin-bottom: 8px;
}

.footer-slogan {
  color: var(--text-on-dark-muted);
  font-size: 14px;
}

.footer-links {
  display: flex;
  flex-wrap: wrap;
  gap: 32px 48px;
  margin-bottom: 32px;
}

.link-group h4 {
  font-size: 15px;
  margin-bottom: 12px;
  color: var(--text-on-dark);
}

.link-group a {
  display: block;
  color: var(--text-on-dark-muted);
  font-size: 13px;
  text-decoration: none;
  padding: 4px 0;
  transition: color 0.2s;
}

.link-group a:hover {
  color: var(--color-primary-on-dark);
}

.footer-bottom {
  border-top: 1px solid var(--border-on-dark);
  padding-top: 16px;
  text-align: center;
}

.footer-bottom p {
  color: var(--text-on-dark-muted);
  font-size: 12px;
}
</style>
