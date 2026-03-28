import axios from "axios";
import {message} from "ant-design-vue";

// 开发：baseURL 为空，请求发到当前页面源（Vite），由 vite.config 代理到 Java，避免 127.0.0.1 vs localhost 跨域
// 生产：整站若与后端同域可配 VITE_API_ORIGIN 为空；否则填完整 Java 根地址（含端口）
const API_BASE =
  import.meta.env.DEV
    ? ''
    : (import.meta.env.VITE_API_ORIGIN as string | undefined) || 'http://118.195.165.9'

const myAxios = axios.create({
  baseURL: API_BASE,
  timeout: 10000,
  withCredentials: true,
})

// 全局请求拦截器
myAxios.interceptors.request.use(
  function (config) {
    // Do something before request is sent
    return config
  },
  function (error) {
    // Do something with request error
    return Promise.reject(error)
  },
)

// 全局响应拦截器
myAxios.interceptors.response.use(
  function (response) {
    const { data } = response
    // 未登录
    if (data.code === 40100) {
      // 不是获取用户信息的请求，并且用户目前不是已经在用户登录页面，则跳转到登录页面
      if (
        !response.request.responseURL.includes('user/get/login') &&
        !window.location.pathname.includes('/user/login')
      ) {
        message.warning('请先登录')
        window.location.href = `/user/login?redirect=${window.location.href}`
      }
    }
    return response
  },
  function (error) {
    // Any status codes that falls outside the range of 2xx cause this function to trigger
    // Do something with response error
    return Promise.reject(error)
  },
)

export default myAxios;
