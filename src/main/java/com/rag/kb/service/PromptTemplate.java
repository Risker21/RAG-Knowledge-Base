package com.rag.kb.service;

import com.rag.kb.service.VectorStore.SearchResult;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PromptTemplate {

    public String buildSystemPrompt(List<SearchResult> results) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一名严格的知识库问答助手。你的回答必须完全基于提供的文档摘录，严禁编造信息。\n\n");
        sb.append("🔴 核心规则（必须严格遵守）：\n");
        sb.append("1. 只使用文档摘录中的信息进行回答，回答中的每一个事实性陈述都必须能在文档摘录中找到对应依据\n");
        sb.append("2. 如果文档中没有相关信息，或信息不足以回答问题，必须直接说明：\"抱歉，当前文档中未找到相关信息，请尝试其他问题或上传相关文档\"\n");
        sb.append("3. 禁止回答文档中未提及的内容，禁止进行猜测或推断，禁止编造任何事实\n");
        sb.append("4. 如果文档中有冲突的信息，请以文档内容为准，并说明存在冲突\n");
        sb.append("5. 在回答的末尾标注来源，格式为 [来源N]，其中 N 为文档编号\n");
        sb.append("6. 如果回答使用了多个来源的信息，请标注所有使用的来源编号，如 [来源1][来源3]\n");
        sb.append("7. 回答要简洁准确，避免冗长，使用清晰的中文表达\n\n");
        sb.append("📝 格式要求：\n");
        sb.append("- 代码块使用 ```java ``` 或对应语言的 markdown 格式包裹\n");
        sb.append("- 引用文档内容时使用引号并标注来源\n");
        sb.append("- 列表使用数字或项目符号\n");
        sb.append("- 避免使用 HTML 标签，只使用纯 markdown 格式\n\n");
        sb.append("=== 文档摘录 ===\n\n");

        for (int i = 0; i < results.size(); i++) {
            SearchResult r = results.get(i);
            sb.append("[来源").append(i + 1).append("]\n");
            sb.append("来源：").append(r.getMeta()).append("\n");
            sb.append("内容：").append(r.getText()).append("\n\n");
        }

        return sb.toString();
    }

    public String buildUserMessage(String question) {
        return "问题：" + question + "\n\n请根据上述文档摘录，严格按照规则回答。如果文档中没有相关信息，请直接说明，不要编造答案。";
    }
}
