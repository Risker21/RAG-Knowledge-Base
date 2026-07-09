package com.rag.kb.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.rag.kb.mapper.ConversationMapper;
import com.rag.kb.mapper.KbMapper;
import com.rag.kb.mapper.MessageMapper;
import com.rag.kb.model.entity.Conversation;
import com.rag.kb.model.entity.Document;
import com.rag.kb.model.entity.KnowledgeBase;
import com.rag.kb.model.entity.Message;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.baomidou.mybatisplus.core.toolkit.Wrappers.lambdaQuery;

@Service
@RequiredArgsConstructor
public class KbService {

    private final KbMapper kbMapper;
    private final DocumentService documentService;
    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;

    public KnowledgeBase create(Long userId, String name, String description) {
        KnowledgeBase kb = new KnowledgeBase();
        kb.setUserId(userId);
        kb.setName(name);
        kb.setDescription(description);
        kbMapper.insert(kb);
        return kb;
    }

    public List<KnowledgeBase> listByUser(Long userId) {
        return kbMapper.selectList(
                new LambdaQueryWrapper<KnowledgeBase>()
                        .eq(KnowledgeBase::getUserId, userId)
                        .orderByDesc(KnowledgeBase::getCreatedAt));
    }

    public KnowledgeBase getById(Long id) {
        return kbMapper.selectById(id);
    }

    public KnowledgeBase getByIdAndUser(Long id, Long userId) {
        return kbMapper.selectOne(
                lambdaQuery(KnowledgeBase.class)
                        .eq(KnowledgeBase::getId, id)
                        .eq(KnowledgeBase::getUserId, userId));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void delete(Long id, Long userId) {
        KnowledgeBase kb = getByIdAndUser(id, userId);
        if (kb == null) {
            throw new RuntimeException("无权删除此知识库");
        }

        List<Document> docs = documentService.listByKb(id);
        for (Document doc : docs) {
            documentService.delete(doc.getId(), userId);
        }

        List<Conversation> conversations = conversationMapper.selectList(
                lambdaQuery(Conversation.class).eq(Conversation::getKbId, id));
        for (Conversation conv : conversations) {
            messageMapper.delete(lambdaQuery(Message.class)
                    .eq(Message::getConversationId, conv.getId()));
            conversationMapper.deleteById(conv.getId());
        }

        kbMapper.deleteById(id);
    }
}
