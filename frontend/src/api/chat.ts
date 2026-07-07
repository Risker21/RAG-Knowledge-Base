import client from './client'
import type { ApiResult, Conversation, Message, ChatResponse } from '@/types'

export const listConversations = (kbId: number) =>
  client.get<ApiResult<Conversation[]>>(`/chat/api/conversations/${kbId}`)

export const startConversation = (kbId: number, title?: string) =>
  client.post<ApiResult<Conversation>>('/chat/api/start',
    new URLSearchParams({ kbId: String(kbId), title: title || '' }),
    { headers: { 'Content-Type': 'application/x-www-form-urlencoded' } })

export const deleteConversation = (id: number) =>
  client.delete(`/chat/api/conversations/${id}`)

export const getMessages = (convId: number) =>
  client.get<ApiResult<Message[]>>(`/chat/api/messages/${convId}`)

export const ask = (data: { conversationId: number; kbId: number; question: string }) =>
  client.post<ApiResult<ChatResponse>>('/chat/api/ask', data)
