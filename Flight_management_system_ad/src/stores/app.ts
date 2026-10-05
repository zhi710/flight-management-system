import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useAppStore = defineStore('app', () => {
  const sidebarCollapsed = ref(false)
  const activeMenu = ref('')

  function toggleSidebar() {
    sidebarCollapsed.value = !sidebarCollapsed.value
  }

  function setActiveMenu(path: string) {
    activeMenu.value = path
  }

  return { sidebarCollapsed, activeMenu, toggleSidebar, setActiveMenu }
})
