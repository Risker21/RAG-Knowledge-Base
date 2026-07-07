import client from './client'
import type { ApiResult, Document } from '@/types'

export const listDocs = (kbId: number) =>
  client.get<ApiResult<Document[]>>(`/doc/api/list/${kbId}`)

export const uploadDoc = (kbId: number, file: File) => {
  const form = new FormData()
  form.append('file', file)
  form.append('kbId', kbId.toString())
  return client.post<ApiResult<any>>('/doc/api/upload', form, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export const deleteDoc = (id: number) =>
  client.delete(`/doc/api/${id}`)
