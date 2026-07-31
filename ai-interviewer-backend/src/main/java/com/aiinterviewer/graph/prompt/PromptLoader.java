package com.aiinterviewer.graph.prompt;

import com.aiinterviewer.exception.BusinessException;
import com.aiinterviewer.common.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Prompt 模板加载器：从 classpath:prompts/ 读取 .md 模板并填充占位符。
 * <p>
 * 模板使用 {placeholder} 语法。加载一次后缓存。
 */
@Slf4j
@Component
public class PromptLoader {

    private final Map<String, String> cache = new HashMap<>();

    /**
     * 加载并填充模板。
     *
     * @param name     模板名（不含扩展名，如 "opening"）
     * @param vars     占位符变量
     * @return 填充后的 prompt
     */
    public String render(String name, Map<String, ?> vars) {
        String template = cache.computeIfAbsent(name, this::loadFromResource);
        if (vars == null || vars.isEmpty()) {
            return template;
        }
        String result = template;
        for (Map.Entry<String, ?> e : vars.entrySet()) {
            String val = e.getValue() == null ? "" : String.valueOf(e.getValue());
            result = result.replace("{" + e.getKey() + "}", val);
        }
        return result;
    }

    private String loadFromResource(String name) {
        String path = "prompts/" + name + ".md";
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new ClassPathResource(path).getInputStream(), StandardCharsets.UTF_8))) {
            String content = reader.lines().collect(Collectors.joining("\n"));
            log.info("加载 prompt 模板: {}", path);
            return content;
        } catch (Exception e) {
            log.error("加载 prompt 模板失败: {}", path, e);
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "加载 prompt 模板失败: " + name);
        }
    }
}
