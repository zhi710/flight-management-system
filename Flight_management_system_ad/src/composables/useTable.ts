import { ref, reactive, onMounted, type Ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

/**
 * 后台列表页的通用状态机。
 *
 * 背景：27 个列表页各自写了同一套样板 —— loading、list、pagination、
 * searchForm、fetchList、handleSearch、resetSearch，合计几百行完全重复的代码。
 * 并且各页的默认 pageSize、错误处理、重置行为都不完全一致。
 *
 * 用法：
 *   const { loading, list, pagination, query, search, reset, changePage, load } = useTable({
 *     api: getPassengerList,
 *     initialQuery: { name: '', idNumber: '', phone: '' },
 *   })
 *
 * 模板里继续用 loading / list / pagination，与改造前完全一致，模板无需改动。
 */

interface PageResult<T> {
  list: T[]
  total: number
}

export interface UseTableOptions<T, Q extends object> {
  /** 列表查询接口，会收到 query + page + pageSize */
  api: (params: any) => Promise<any>
  /** 查询条件初始值，同时作为「重置」的还原目标 */
  initialQuery?: Q
  /** 每页条数，默认 20 */
  pageSize?: number
  /** 是否在 onMounted 时自动查询，默认 true */
  immediate?: boolean
  /**
   * 从响应里解析列表与总数。
   * 默认兼容后端常见的三种写法：list / records，pagination.total / total
   */
  pick?: (res: any) => PageResult<T>
  /** 加载失败时的提示语 */
  errorText?: string
}

export function useTable<T = any, Q extends object = Record<string, any>>(
  options: UseTableOptions<T, Q>,
) {
  const { api, initialQuery, pageSize = 20, immediate = true, errorText = '加载失败' } = options

  const loading = ref(false)
  const list = ref([]) as Ref<T[]>
  const pagination = reactive({ page: 1, pageSize, total: 0 })
  const query = reactive({ ...(initialQuery ?? {}) }) as unknown as Q

  const defaultPick = (res: any): PageResult<T> => ({
    list: (res?.list ?? res?.records ?? []) as T[],
    total: res?.pagination?.total ?? res?.total ?? 0,
  })

  /** 按当前 query 与分页参数拉取列表 */
  async function load() {
    loading.value = true
    try {
      const res = await api({
        ...query,
        page: pagination.page,
        pageSize: pagination.pageSize,
      })
      const { list: rows, total } = (options.pick ?? defaultPick)(res)
      list.value = rows
      pagination.total = total
    } catch (err: any) {
      ElMessage.error(err?.message || errorText)
      list.value = []
      pagination.total = 0
    } finally {
      loading.value = false
    }
  }

  /** 条件变了：回到第 1 页再查，否则会停在越界的页码上 */
  function search() {
    pagination.page = 1
    return load()
  }

  /** 还原查询条件并重新查询 */
  function reset() {
    if (initialQuery) Object.assign(query, initialQuery)
    return search()
  }

  function changePage(page: number) {
    pagination.page = page
    return load()
  }

  function changePageSize(size: number) {
    pagination.pageSize = size
    pagination.page = 1
    return load()
  }

  if (immediate) onMounted(load)

  return {
    loading,
    list,
    pagination,
    query,
    load,
    search,
    reset,
    changePage,
    changePageSize,
  }
}

export interface ConfirmDeleteOptions {
  /** 二次确认的提示语 */
  message?: string
  /** 实际执行删除的接口调用 */
  action: () => Promise<any>
  /** 删除成功后的回调，一般传 () => load() 或 () => search() */
  onDone?: () => void
  /** 成功提示语 */
  successText?: string
  title?: string
}

/**
 * 删除前二次确认 → 执行 → 提示 → 刷新。
 * 原先这段 try/catch/catch/prompt 在每个页面重复了 20 多次，
 * 而且不少页面漏了 catch，删除失败时静默无反馈。
 */
export async function confirmDelete(options: ConfirmDeleteOptions) {
  const { message = '确定要删除这条记录吗？', action, onDone, successText = '已删除', title = '提示' } = options

  try {
    await ElMessageBox.confirm(message, title, {
      type: 'warning',
      confirmButtonText: '确定',
      cancelButtonText: '取消',
    })
  } catch {
    // 用户点了取消，不是错误
    return false
  }

  try {
    await action()
    ElMessage.success(successText)
    onDone?.()
    return true
  } catch (err: any) {
    ElMessage.error(err?.message || '删除失败')
    return false
  }
}
