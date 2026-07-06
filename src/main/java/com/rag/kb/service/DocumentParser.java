package com.rag.kb.service;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.ToTextContentHandler;
import org.springframework.stereotype.Service;
import org.xml.sax.SAXException;

import java.io.*;
import java.nio.file.Files;
import java.util.Set;

@Service
public class DocumentParser {
    private final Parser parser = new AutoDetectParser();

    private static final Set<String> SUPPORTED_TYPES = Set.of(
            "pdf", "txt", "md", "docx", "pptx", "html", "htm", "csv"
    );

    public String parse(File file, String fileType) throws Exception {
        String lower = fileType.toLowerCase();
        if (!SUPPORTED_TYPES.contains(lower)) {
            throw new RuntimeException("不支持的文件类型: " + fileType +
                    "（支持: " + String.join(", ", SUPPORTED_TYPES) + "）");
        }
        try (InputStream input = Files.newInputStream(file.toPath())) {
            Metadata metadata = new Metadata();
            ToTextContentHandler handler = new ToTextContentHandler();
            parser.parse(input, handler, metadata, new ParseContext());
            String text = handler.toString();
            if (text == null || text.isBlank()) {
                throw new RuntimeException("文件内容为空或解析结果为空白");
            }
            return text;
        } catch (TikaException | SAXException e) {
            throw new RuntimeException("Tika 解析失败: " + e.getMessage(), e);
        }
    }
}
