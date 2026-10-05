<template>
  <div class="page-container">
    <!-- 概览 + 筛选 -->
    <div class="card-panel">
      <div class="page-header">
        <div class="page-title">航班动态地图</div>
        <div class="page-tools">
          <el-date-picker
            v-model="date"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="航班日期（默认全部）"
            clearable
            style="width: 200px"
          />
          <el-button type="primary" :loading="loading" @click="refresh">
            <el-icon><Search /></el-icon> 刷新
          </el-button>
        </div>
      </div>

      <div class="stat-row" style="margin-bottom: 0">
        <div class="stat-card">
          <div class="stat-icon" style="background: var(--color-primary-soft); color: var(--color-primary)">
            <el-icon><Position /></el-icon>
          </div>
          <div class="stat-content">
            <div class="stat-value">{{ stats.airportCount }}</div>
            <div class="stat-label">覆盖机场</div>
          </div>
        </div>
        <div class="stat-card">
          <div class="stat-icon" style="background: var(--color-success-soft); color: var(--color-success)">
            <el-icon><Share /></el-icon>
          </div>
          <div class="stat-content">
            <div class="stat-value">{{ stats.routeCount }}</div>
            <div class="stat-label">航线数</div>
          </div>
        </div>
        <div class="stat-card">
          <div class="stat-icon" style="background: var(--color-warning-soft); color: var(--color-warning)">
            <el-icon><Promotion /></el-icon>
          </div>
          <div class="stat-content">
            <div class="stat-value">{{ stats.flightCount }}</div>
            <div class="stat-label">航班班次</div>
          </div>
        </div>
        <div class="stat-card">
          <div class="stat-icon" style="background: var(--color-info-soft); color: var(--color-info)">
            <el-icon><Location /></el-icon>
          </div>
          <div class="stat-content">
            <div class="stat-value">{{ stats.cityCount }}</div>
            <div class="stat-label">覆盖城市</div>
          </div>
        </div>
      </div>
    </div>

    <!-- 地图 -->
    <div class="card-panel">
      <div class="map-head">
        <div class="page-title">航线网络</div>
        <div class="map-legend">
          <span class="legend-item"><i class="dot" style="background: var(--color-primary)"></i>机场</span>
          <span class="legend-item"><i class="line" style="background: var(--color-primary)"></i>航线（线宽随航班量变化）</span>
        </div>
      </div>

      <div class="map-wrap">
        <div ref="mapRef" class="map-canvas"></div>

        <div v-if="mapError" class="map-overlay">
          <el-icon :size="30" color="var(--color-warning)"><WarningFilled /></el-icon>
          <p>{{ mapError }}</p>
          <el-button size="small" @click="initMap">重试</el-button>
        </div>
        <div v-else-if="!mapReady" class="map-overlay">
          <el-icon class="is-loading" :size="28" color="var(--color-primary)"><Loading /></el-icon>
          <p>地图加载中…</p>
        </div>
      </div>

      <div class="map-hint">
        底图由腾讯地图提供（GCJ-02 火星坐标系，机场坐标已由 WGS-84 转换）；平台暂无实时飞机位置数据，本图为航线网络示意。
      </div>
    </div>

    <!-- 航线清单 -->
    <div class="card-panel">
      <div class="page-header">
        <div class="page-title">航线清单</div>
        <span class="map-sub">共 {{ routes.length }} 条</span>
      </div>

      <el-table scrollbar-always-on :data="routes" stripe style="width: 100%" empty-text="当前条件下暂无航线数据">
        <el-table-column label="航线" min-width="200">
          <template #default="{ row }">
            <div class="route-cell">
              <span class="route-code">{{ row.from }}</span>
              <el-icon class="route-arrow" color="var(--text-secondary)"><Right /></el-icon>
              <span class="route-code">{{ row.to }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="起点" min-width="160">
          <template #default="{ row }">{{ airportName(row.from) }}</template>
        </el-table-column>
        <el-table-column label="终点" min-width="160">
          <template #default="{ row }">{{ airportName(row.to) }}</template>
        </el-table-column>
        <el-table-column label="航班量" width="160">
          <template #default="{ row }">
            <div class="count-cell">
              <div class="count-bar">
                <div class="count-bar__fill" :style="{ width: barWidth(row.flightCount) }"></div>
              </div>
              <span class="tnum">{{ row.flightCount }}</span>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { ref, reactive, computed, onMounted, onBeforeUnmount } from 'vue'
import { getRouteMap } from '@/api/flights'
import { tokens, ramp } from '@/assets/styles/palette'

interface AirportPoint {
  code: string
  name: string
  city: string
  longitude: number
  latitude: number
  flightCount: number
}

interface RouteLine {
  route: string
  from: string
  to: string
  fromLng: number
  fromLat: number
  toLng: number
  toLat: number
  flightCount: number
}

const mapRef = ref<HTMLDivElement>()
const mapReady = ref(false)
const mapError = ref('')
const loading = ref(false)
const date = ref('')

const airports = ref<AirportPoint[]>([])
const routes = ref<RouteLine[]>([])
const stats = reactive({ airportCount: 0, routeCount: 0, flightCount: 0, cityCount: 0 })

const airportMap = computed(() => {
  const m: Record<string, AirportPoint> = {}
  airports.value.forEach((a) => { m[a.code] = a })
  return m
})

const maxCount = computed(() => Math.max(1, ...routes.value.map((r) => Number(r.flightCount) || 1)))

/** eslint-disable @typescript-eslint/no-explicit-any */
let map: any = null
let markerLayer: any = null
let lineLayer: any = null
let infoWindow: any = null
let sdkPromise: Promise<void> | null = null

/* ---------------- 腾讯地图 SDK（合规：默认场景走本地代理，前端零 key） ---------------- */
function loadTMapSdk(): Promise<void> {
  if (sdkPromise) return sdkPromise
  sdkPromise = new Promise((resolve, reject) => {
    const w = window as any
    if (w.TMap) { resolve(); return }
    // 必须在 SDK 之前配置；占位符由 WorkBuddy 运行时替换，务必原样保留
    w._TMapSecurityConfig = {
      serviceHost: 'http://127.0.0.1:__WB_HTTP_PORT__/_TMapService/_wbt/__WB_TMAP_SECRET__',
    }
    const script = document.createElement('script')
    script.src = 'https://map.qq.com/api/gljs?v=1.exp'
    script.async = true
    script.onload = () => resolve()
    script.onerror = () => reject(new Error('地图 SDK 加载失败，请检查网络后重试'))
    document.head.appendChild(script)
  })
  return sdkPromise
}

/* ---------------- WGS-84 → GCJ-02 ---------------- */
const PI = Math.PI
const A = 6378245.0
const EE = 0.00669342162296594323

function outOfChina(lng: number, lat: number) {
  return lng < 72.004 || lng > 137.8347 || lat < 0.8293 || lat > 55.8271
}
function transformLat(x: number, y: number) {
  let ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * Math.sqrt(Math.abs(x))
  ret += ((20.0 * Math.sin(6.0 * x * PI) + 20.0 * Math.sin(2.0 * x * PI)) * 2.0) / 3.0
  ret += ((20.0 * Math.sin(y * PI) + 40.0 * Math.sin((y / 3.0) * PI)) * 2.0) / 3.0
  ret += ((160.0 * Math.sin((y / 12.0) * PI) + 320 * Math.sin((y * PI) / 30.0)) * 2.0) / 3.0
  return ret
}
function transformLng(x: number, y: number) {
  let ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * Math.sqrt(Math.abs(x))
  ret += ((20.0 * Math.sin(6.0 * x * PI) + 20.0 * Math.sin(2.0 * x * PI)) * 2.0) / 3.0
  ret += ((20.0 * Math.sin(x * PI) + 40.0 * Math.sin((x / 3.0) * PI)) * 2.0) / 3.0
  ret += ((150.0 * Math.sin((x / 12.0) * PI) + 300.0 * Math.sin((x / 30.0) * PI)) * 2.0) / 3.0
  return ret
}
function wgs84ToGcj02(lng: number, lat: number): [number, number] {
  if (!Number.isFinite(lng) || !Number.isFinite(lat) || outOfChina(lng, lat)) return [lng, lat]
  let dLat = transformLat(lng - 105.0, lat - 35.0)
  let dLng = transformLng(lng - 105.0, lat - 35.0)
  const radLat = (lat / 180.0) * PI
  let magic = Math.sin(radLat)
  magic = 1 - EE * magic * magic
  const sqrtMagic = Math.sqrt(magic)
  dLat = (dLat * 180.0) / (((A * (1 - EE)) / (magic * sqrtMagic)) * PI)
  dLng = (dLng * 180.0) / ((A / sqrtMagic) * Math.cos(radLat) * PI)
  return [lng + dLng, lat + dLat]
}

/* ---------------- 地图初始化 / 渲染 ---------------- */
const MARKER_SVG = 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(
  `<svg xmlns="http://www.w3.org/2000/svg" width="26" height="26">` +
  `<circle cx="13" cy="13" r="8" fill="${tokens.colorPrimary}" stroke="#ffffff" stroke-width="2.5"/>` +
  `<circle cx="13" cy="13" r="2.4" fill="#ffffff"/></svg>`,
)

async function initMap() {
  mapError.value = ''
  try {
    await loadTMapSdk()
    const TMap = (window as any).TMap
    if (!mapRef.value) return

    // 合规：不传 mapStyleId，使用默认矢量底图
    map = new TMap.Map(mapRef.value, {
      center: new TMap.LatLng(34.0, 108.9),
      zoom: 4.4,
      viewMode: '2D',
    })

    markerLayer = new TMap.MultiMarker({
      map,
      styles: {
        default: new TMap.MarkerStyle({
          width: 26,
          height: 26,
          anchor: { x: 13, y: 13 },
          src: MARKER_SVG,
        }),
      },
      geometries: [],
    })

    lineLayer = new TMap.MultiPolyline({
      map,
      styles: {
        // 注意：TMap 的 PolylineStyle.width 必须是整数，传 1.5/2.5 会被判为
        // 「width属性无效」并丢弃该样式（线路不渲染），故此处统一用整数档。
        thin: new TMap.PolylineStyle({ color: ramp.primary.light3, width: 2 }),
        mid: new TMap.PolylineStyle({ color: tokens.colorPrimary, width: 3 }),
        thick: new TMap.PolylineStyle({ color: ramp.primary.dark2, width: 5 }),
      },
      geometries: [],
    })

    // InfoWindow 构造函数要求 position，缺省会抛错；先给占位坐标再立即关闭
    infoWindow = new TMap.InfoWindow({
      map,
      position: new TMap.LatLng(34.0, 108.9),
      offset: { x: 0, y: -14 },
      enableCustom: false,
    })
    infoWindow.close()

    markerLayer.on('click', (evt: any) => {
      const p = evt?.geometry?.properties || {}
      const [lng, lat] = wgs84ToGcj02(Number(p.longitude), Number(p.latitude))
      const city = p.city ? ` · ${p.city}` : ''
      infoWindow.open()
      infoWindow.setPosition(new TMap.LatLng(lat, lng))
      infoWindow.setContent(
        `<div style="padding:4px 2px;font-size:13px;line-height:1.6;">` +
        `<div style="font-weight:600;">${p.code} ${p.name || ''}</div>` +
        `<div style="color:${tokens.textSecondary}">${city.replace(/^ · /, '')}</div>` +
        `<div>航班量：<b>${p.flightCount ?? 0}</b></div></div>`,
      )
    })

    mapReady.value = true
    renderMap()
  } catch (e: any) {
    mapError.value = e?.message || '地图加载失败'
  }
}

function renderMap() {
  if (!map || !markerLayer || !lineLayer) return
  const TMap = (window as any).TMap

  // 机场点位
  markerLayer.setGeometries(
    airports.value.map((a) => {
      const [lng, lat] = wgs84ToGcj02(Number(a.longitude), Number(a.latitude))
      return { id: a.code, styleId: 'default', position: new TMap.LatLng(lat, lng), properties: a }
    }),
  )

  // 航线（线宽按航班量分档）
  const max = maxCount.value
  lineLayer.setGeometries(
    routes.value.map((r) => {
      const [flng, flat] = wgs84ToGcj02(Number(r.fromLng), Number(r.fromLat))
      const [tlng, tlat] = wgs84ToGcj02(Number(r.toLng), Number(r.toLat))
      const ratio = (Number(r.flightCount) || 1) / max
      const styleId = ratio > 0.66 ? 'thick' : ratio > 0.33 ? 'mid' : 'thin'
      return { id: r.route, styleId, paths: [new TMap.LatLng(flat, flng), new TMap.LatLng(tlat, tlng)] }
    }),
  )

  fitView()
}

/** 让全部要点落入视野；fitBounds 不可用时退回手动中心+缩放 */
function fitView() {
  if (!map) return
  const TMap = (window as any).TMap
  const pts: [number, number][] = []
  airports.value.forEach((a) => pts.push(wgs84ToGcj02(Number(a.longitude), Number(a.latitude))))
  if (!pts.length) return
  let minLng = pts[0]![0], maxLng = pts[0]![0], minLat = pts[0]![1], maxLat = pts[0]![1]
  pts.forEach(([lng, lat]) => {
    minLng = Math.min(minLng, lng); maxLng = Math.max(maxLng, lng)
    minLat = Math.min(minLat, lat); maxLat = Math.max(maxLat, lat)
  })
  try {
    map.fitBounds(
      new TMap.LatLngBounds(new TMap.LatLng(minLat, minLng), new TMap.LatLng(maxLat, maxLng)),
      { padding: 80 },
    )
  } catch {
    const span = Math.max(maxLng - minLng, (maxLat - minLat) * 1.4)
    const zoom = span > 40 ? 4 : span > 20 ? 5 : span > 10 ? 5.6 : span > 5 ? 6.4 : 7.2
    map.setCenter(new TMap.LatLng((minLat + maxLat) / 2, (minLng + maxLng) / 2))
    map.setZoom(zoom)
  }
}

/* ---------------- 数据 ---------------- */
async function fetchData() {
  loading.value = true
  try {
    const data = (await getRouteMap(date.value ? { date: date.value } : undefined)) as any
    airports.value = data?.airports || []
    routes.value = data?.routes || []
    stats.airportCount = data?.airportCount ?? airports.value.length
    stats.routeCount = data?.routeCount ?? routes.value.length
    stats.flightCount = data?.flightCount ?? 0
    stats.cityCount = new Set(airports.value.map((a) => a.city).filter(Boolean)).size
    if (mapReady.value) renderMap()
  } catch (e: any) {
    ElMessage.error(e?.message || '航线数据加载失败')
  } finally {
    loading.value = false
  }
}

function refresh() {
  fetchData()
}

function airportName(code: string) {
  return airportMap.value[code]?.name || code
}

function barWidth(count: number) {
  const pct = Math.round(((Number(count) || 0) / maxCount.value) * 100)
  return `${Math.max(6, pct)}%`
}

onMounted(() => {
  initMap()
  fetchData()
})

onBeforeUnmount(() => {
  try {
    markerLayer?.setMap?.(null)
    lineLayer?.setMap?.(null)
    infoWindow?.destroy?.()
    map?.destroy?.()
  } catch {
    /* 忽略销毁异常 */
  }
  map = null
  markerLayer = null
  lineLayer = null
  infoWindow = null
})
</script>

<style scoped>
.page-tools {
  display: flex;
  align-items: center;
  gap: var(--space-inline);
}

.map-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--space-section);
}

.map-legend {
  display: flex;
  align-items: center;
  gap: var(--space-section);
  font-size: var(--font-size-sm);
  color: var(--text-secondary);
}

.legend-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.legend-item .dot {
  width: 10px;
  height: 10px;
  border-radius: var(--radius-full);
  display: inline-block;
}

.legend-item .line {
  width: 18px;
  height: 3px;
  border-radius: var(--radius-full);
  display: inline-block;
}

.map-wrap {
  position: relative;
  height: 520px;
  border: 1px solid var(--border-color-lighter);
  border-radius: var(--radius-base);
  overflow: hidden;
  background: var(--bg-muted);
}

.map-canvas {
  width: 100%;
  height: 100%;
}

.map-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  background: var(--bg-muted);
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
}

.map-overlay p {
  margin: 0;
}

.map-hint {
  margin-top: var(--space-inline);
  font-size: var(--font-size-xs);
  color: var(--text-secondary);
  line-height: 1.6;
}

.map-sub {
  font-size: var(--font-size-sm);
  color: var(--text-secondary);
}

.route-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.route-code {
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  color: var(--text-primary);
}

.route-arrow {
  font-size: 12px;
}

.count-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}

.count-bar {
  flex: 1;
  min-width: 40px;
  height: 6px;
  border-radius: var(--radius-full);
  background: var(--bg-muted-strong);
  overflow: hidden;
}

.count-bar__fill {
  height: 100%;
  border-radius: var(--radius-full);
  background: var(--color-primary);
}

.count-cell .tnum {
  min-width: 28px;
  text-align: right;
  color: var(--text-primary);
}
</style>
