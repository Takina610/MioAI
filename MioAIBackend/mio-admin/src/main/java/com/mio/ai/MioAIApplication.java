package com.mio.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * @author: Takina
 * @date: 2026/3/29 21:45
 * @description:
 */

@EnableScheduling
@SpringBootApplication
public class MioAIApplication {
    public static void main(String[] args) {
        SpringApplication.run(MioAIApplication.class);
    }
}
