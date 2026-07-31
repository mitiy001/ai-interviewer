package com.aiinterviewer.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * STT 语音识别 Service。
 * <p>
 * 调用阿里云 DashScope Paraformer / SenseVoice 语音识别 API，
 * 接收前端上传的音频文件，返回识别文本。
 */
public interface SttService {

    /**
     * 识别音频文件中的语音。
     *
     * @param file 前端录制的音频文件（webm/wav/mp3）
     * @return 识别出的文本
     */
    String recognize(MultipartFile file);
}
