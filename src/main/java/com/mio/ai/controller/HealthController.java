package com.mio.ai.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author: Takina
 * @date: 2026/3/29 21:56
 * @description:
 */

@RestController
public class HealthController {

    @RequestMapping("/health")
    public String ping() {
        return "pong";
    }
}
