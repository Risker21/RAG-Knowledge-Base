import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { Conversation, Message, Reference } from '@/types'
import * as chatApi from '@/api/chat'

export const useChatStore = defineStore('chat', () => {
  const conversations = ref<Conversation[]>([])
  const messages = ref<Message[]>([])
  const currentConvId = ref<number | null>(null)
  const loading = ref(false)

  async function loadConversations(kbId: number) {
    loading.value = true
    try {
      const res = await chatApi.listConversations(kbId)
      if (res.data.code === 200) {
        conversations.value = res.data.data
      }
    } finally {
      loading.value = false
    }
  }

  async function loadMessages(convId: number) {
    const res = await chatApi.getMessages(convId)
    if (res.data.code === 200) {
      messages.value = res.data.data
    }
  }

  async function startConversation(kbId: number, title?: string) {
    const res = await chatApi.startConversation(kbId, title)
    if (res.data.code === 200) {
      const conv = res.data.data as any
      conversations.value.unshift({ id: conv.id, title: conv.title, userId: 0, kbId, createdAt: '' })
      currentConvId.value = conv.id
      messages.value = []
    }
    return res.data
  }

  async function removeConversation(id: number) {
    await chatApi.deleteConversation(id)
    conversations.value = conversations.value.filter(c => c.id !== id)
    if (currentConvId.value === id) {
      currentConvId.value = null
      messages.value = []
    }
  }

  return {
    conversations, messages, currentConvId, loading,
    loadConversations, loadMessages, startConversation, removeConversation
  }
})
