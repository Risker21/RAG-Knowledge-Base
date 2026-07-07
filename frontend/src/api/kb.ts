import client from './client'
import type { ApiResult, KnowledgeBase } from '@/types'

export const listKb = () =>
  client.get<ApiResult<KnowledgeBase[]>>('/kb/api/list')

export const createKb = (name: string, description: string) =>
  client.post<ApiResult<KnowledgeBase>>('/kb/api/create', { name, description })

export const deleteKb = (id: number) =>
  client.delete(`/kb/api/${id}`)
