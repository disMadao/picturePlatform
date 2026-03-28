import { ref } from 'vue'
import { defineStore } from 'pinia'
import { getLoginUserUsingGet } from '@/api/userController.ts'

/**
 * 存储登录用户信息的状态
 */
export const useLoginUserStore = defineStore('loginUser', () => {
  const loginUser = ref<API.LoginUserVO>({
    userName: '未登录',
  })

  /**
   * 远程获取登录用户信息
   */
  async function fetchLoginUser() {
    const res = await getLoginUserUsingGet()
    if (res.data.code === 0 && res.data.data) {
      const next = res.data.data
      // 合并而非整对象替换，避免接口偶发缺 id 时把已登录态清空（智能助手页会拉不到历史会话）
      loginUser.value = {
        ...loginUser.value,
        ...next,
        id: next.id ?? loginUser.value.id,
      }
    }
    // // 测试用户登录，3 秒后自动登录
    // setTimeout(() => {
    //   loginUser.value = { userName: '测试用户', id: 1 }
    // }, 3000)
  }

  /**
   * 设置登录用户
   * @param newLoginUser
   */
  function setLoginUser(newLoginUser: any) {
    loginUser.value = newLoginUser
  }

  // 返回
  return { loginUser, fetchLoginUser, setLoginUser }
})
