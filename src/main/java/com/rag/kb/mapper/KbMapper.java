package com.rag.kb.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rag.kb.model.entity.KnowledgeBase;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface KbMapper extends BaseMapper<KnowledgeBase> {
}
