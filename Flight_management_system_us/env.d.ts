/// <reference types="vite/client" />

declare module 'nprogress' {
  const NProgress: {
    start: () => void
    done: () => void
    configure: (options: { showSpinner?: boolean }) => void
  }
  export default NProgress
}

declare module 'element-plus/es/locale/lang/zh-cn' {
  const zhCn: any
  export default zhCn
}
