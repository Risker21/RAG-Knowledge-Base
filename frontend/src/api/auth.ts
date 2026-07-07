import client from './client'
import type { ApiResult, User } from '@/types'

export const login = (username: string, password: string, captcha: string) =>
  client.post<ApiResult<User>>('/api/auth/login', { username, password, captcha })

export const register = (username: string, password: string, confirmPassword: string, captcha: string) =>
  client.post<ApiResult<User>>('/api/auth/register', { username, password, confirmPassword, captcha })

export const logout = () =>
  client.get<ApiResult<null>>('/api/auth/logout')
