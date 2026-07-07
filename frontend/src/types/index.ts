export interface User {
  id: number
  username: string
  createdAt: string
}

export interface KnowledgeBase {
  id: number
  userId: number
  name: string
  description: string
  createdAt: string
}

export interface Document {
  id: number
  kbId: number
  userId: number
  originalName: string
  filename: string
  fileType: string
  fileSize: number
  status: number
  chunkCount: number
  errorMsg: string
  createdAt: string
}

export interface Conversation {
  id: number
  userId: number
  kbId: number
  title: string
  createdAt: string
}

export interface Message {
  id: number
  conversationId: number
  role: number
  content: string
  referencesJson: string
  createdAt: string
}

export interface ApiResult<T> {
  code: number
  message: string
  data: T
}

export interface ChatResponse {
  answer: string
  references: Array<{ title: string; content: string }>
  conversationId: number
}

export interface Reference {
  index: number
  source: string
  snippet: string
  score: number
}
