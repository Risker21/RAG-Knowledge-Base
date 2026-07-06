package com.rag.kb.service;

import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;

@Service
public class TextChunker {

    public List<Chunk> split(String text, String filename) {
        List<Chunk> chunks = new ArrayList<>();
        // 1. 先按 Markdown 标题切
        String[] sections = text.split("(?m)^#+\\s+");
        int globalIdx = 0;
        for (String section : sections) {
            if (section.isBlank()) continue;
            // 2. 再按段落切
            String[] paragraphs = section.split("\\n\\s*\\n");
            StringBuilder currentChunk = new StringBuilder();
            for (String para : paragraphs) {
                para = para.trim();
                if (para.isBlank()) continue;
                if (currentChunk.length() + para.length() > 500 && currentChunk.length() > 0) {
                    chunks.add(new Chunk(globalIdx++, currentChunk.toString().trim(), filename));
                    currentChunk = new StringBuilder();
                }
                if (currentChunk.length() > 0) currentChunk.append("\n");
                currentChunk.append(para);
            }
            if (currentChunk.length() > 0) {
                chunks.add(new Chunk(globalIdx++, currentChunk.toString().trim(), filename));
            }
        }
        // 如果没切出任何块（小文档），整体作为一块
        if (chunks.isEmpty() && !text.isBlank()) {
            chunks.add(new Chunk(0, text.trim(), filename));
        }
        return chunks;
    }

    public static class Chunk {
        private int index;
        private String content;
        private String sourceFile;

        public Chunk(int index, String content, String sourceFile) {
            this.index = index;
            this.content = content;
            this.sourceFile = sourceFile;
        }

        public int getIndex() { return index; }
        public String getContent() { return content; }
        public String getSourceFile() { return sourceFile; }
    }
}
