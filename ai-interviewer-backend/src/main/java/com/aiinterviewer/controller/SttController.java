package com.aiinterviewer.controller;

import com.aiinterviewer.service.SttService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * STT 语音识别 REST 接口。
 * <p>
 * POST /api/stt  上传音频文件，返回识别文本。
 * 前端用 MediaRecorder 录音后上传到本接口。
 */
@Slf4j
@RestController
@RequestMapping("/api/stt")
@RequiredArgsConstructor
public class SttController {

    private final SttService sttService;

    @PostMapping
    public Map<String, String> recognize(@RequestParam("file") MultipartFile file) {
        String text = sttService.recognize(file);
        return Map.of("text", text);
    }
}
