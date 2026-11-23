import axios from 'axios'

const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 15000,
  headers: { 'X-Requested-With': 'joykit' },
})

request.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401 && window.location.pathname !== '/login') {
      const redirect = window.location.pathname + window.location.search + window.location.hash
      window.location.replace(`/login?redirect=${encodeURIComponent(redirect)}`)
    }
    return Promise.reject(error)
  },
)

export default request
