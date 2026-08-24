package com.rag.kb;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
@EnableAsync // 开启异步任务支持
@SpringBootApplication
public class RagKbApplication {
    public static void main(String[] args) {
        SpringApplication.run(RagKbApplication.class, args);
    }
}
