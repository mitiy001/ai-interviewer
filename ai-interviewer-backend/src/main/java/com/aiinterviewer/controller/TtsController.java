package com.aiinterviewer.controller;

import com.aiinterviewer.dto.req.TtsReq;
import com.aiinterviewer.service.TtsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * TTS 语音合成 REST 接口。
 * <p>
 * POST /api/tts  合成语音，返回 MP3 音频流。
 * 前端优先调用本接口；若失败则回退浏览器内置 TTS。
 */
@Slf4j
@RestController
@RequestMapping("/api/tts")
@RequiredArgsConstructor
public class TtsController {

    private final TtsService ttsService;

    @PostMapping
    public ResponseEntity<byte[]> synthesize(@Valid @RequestBody TtsReq req) {
        byte[] mp3 = ttsService.synthesize(req.getText(), req.getVoice());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.valueOf("audio/mpeg"));
        headers.setCacheControl("no-store");
        return ResponseEntity.ok().headers(headers).body(mp3);
    }
}
