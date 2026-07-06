package com.rag.kb.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rag.kb.model.entity.Message;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MessageMapper extends BaseMapper<Message> {}
