package com.aiinterviewer.parser;

import com.aiinterviewer.common.ResultCode;
import com.aiinterviewer.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 题库解析器，支持两种格式：
 * <ol>
 *   <li>JSON（结构化，推荐）：
 *     <pre>{@code
 * {
 *   "name": "Java 题库",
 *   "description": "可选",
 *   "questions": [
 *     {
 *       "type": "theory",            // theory/scenario/project，默认 theory
 *       "difficulty": 2,             // 1/2/3，默认 1
 *       "content": "题目内容",
 *       "standardAnswer": "标准答案",  // 可选
 *       "scoringPoints": ["点1","点2"] // 可选
 *     }
 *   ]
 * }
 *     }</pre>
 *   </li>
 *   <li>MD（轻量，每题以 {@code ## Q1} / {@code ## } 开头分隔）：
 *     <pre>
 * # 题库名（首行 # 开头可选，作为题库名）
 *
 * ## Q1
 * [type=theory, difficulty=2]
 * 题目内容（多行）
 *
 * ### 标准答案
 * 答案内容
 *
 * ### 评分点
 * - 点1
 * - 点2
 *
 * ## Q2
 * ...
 *     </pre>
 *     其中 {@code [type=.., difficulty=..]} 元数据行可选，{@code ### 标准答案}/{@code ### 评分点} 段可选。
 *   </li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QuestionBankParser {

    private final ObjectMapper objectMapper;

    private static final Pattern META_LINE = Pattern.compile(
            "^\\[(?:type\\s*=\\s*(\\w+)\\s*,?\\s*)?(?:difficulty\\s*=\\s*(\\d)\\s*,?\\s*)?\\]\\s*$");

    /** 解析结果 */
    public record ParsedBank(String name, String description, List<ParsedQuestion> questions) {
    }

    /**
     * 按 content-type / 文件名后缀选择解析方式
     */
    public ParsedBank parse(String filename, String contentType, String text) {
        String lower = filename == null ? "" : filename.toLowerCase();
        boolean isJson = (contentType != null && contentType.contains("json")) || lower.endsWith(".json");
        if (isJson) {
            return parseJson(text);
        }
        // 其余按 MD 解析
        return parseMd(text);
    }

    // ---------- JSON ----------

    @SuppressWarnings("unchecked")
    public ParsedBank parseJson(String text) {
        try {
            Map<String, Object> root = objectMapper.readValue(text, Map.class);
            String name = (String) root.getOrDefault("name", "未命名题库");
            String description = (String) root.get("description");
            List<Map<String, Object>> rawQs = (List<Map<String, Object>>) root.get("questions");
            if (rawQs == null || rawQs.isEmpty()) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "题库 JSON 缺少 questions 数组或为空");
            }
            List<ParsedQuestion> list = new ArrayList<>(rawQs.size());
            for (Map<String, Object> q : rawQs) {
                ParsedQuestion pq = new ParsedQuestion();
                pq.setType(asString(q.get("type"), "theory"));
                pq.setDifficulty(asInt(q.get("difficulty"), 1));
                pq.setContent(asString(q.get("content"), null));
                if (pq.getContent() == null || pq.getContent().isBlank()) {
                    throw new BusinessException(ResultCode.PARAM_ERROR, "存在 content 为空的题目");
                }
                pq.setStandardAnswer(asString(q.get("standardAnswer"), null));
                Object sp = q.get("scoringPoints");
                if (sp instanceof List<?> l) {
                    List<String> pts = new ArrayList<>(l.size());
                    for (Object o : l) {
                        pts.add(String.valueOf(o));
                    }
                    pq.setScoringPoints(pts);
                }
                list.add(pq);
            }
            return new ParsedBank(name, description, list);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("题库 JSON 解析失败", e);
            throw new BusinessException(ResultCode.PARAM_ERROR, "题库 JSON 解析失败: " + e.getMessage());
        }
    }

    // ---------- MD ----------

    public ParsedBank parseMd(String text) {
        if (text == null || text.isBlank()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "题库 MD 内容为空");
        }
        // 提取题库名（首行 # xxx）
        String name = "未命名题库";
        String description = null;
        String[] lines = text.split("\\r?\\n");
        if (lines.length > 0 && lines[0].trim().startsWith("# ") && !lines[0].trim().startsWith("## ")) {
            name = lines[0].trim().substring(2).trim();
        }

        // 按 "## " / "### " 切块；遇到第一个分隔符之前的行（题库名/说明）全部忽略
        List<String> blocks = new ArrayList<>();
        StringBuilder cur = null;
        for (String line : lines) {
            String t = line.trim();
            if (t.startsWith("## ") || t.startsWith("### ")) {
                if (cur != null) {
                    blocks.add(cur.toString());
                }
                cur = new StringBuilder();
            }
            if (cur != null) {
                cur.append(line).append('\n');
            }
        }
        if (cur != null) {
            blocks.add(cur.toString());
        }

        List<ParsedQuestion> questions = new ArrayList<>();
        for (String block : blocks) {
            ParsedQuestion pq = parseMdBlock(block);
            if (pq != null) {
                questions.add(pq);
            }
        }
        log.info("MD解析完成: 总行数={}, 切块数={}, 有效题目数={}", lines.length, blocks.size(), questions.size());
        if (questions.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "未解析到题目，请使用 '## ' 或 '### ' 开头分隔每道题");
        }
        return new ParsedBank(name, description, questions);
    }

    private ParsedQuestion parseMdBlock(String block) {
        String[] lines = block.split("\\r?\\n");
        String type = "theory";
        int difficulty = 1;
        StringBuilder content = new StringBuilder();
        StringBuilder answer = new StringBuilder();
        List<String> scoringPoints = new ArrayList<>();

        // 段落标记：0=头部/题干，1=标准答案，2=评分点
        int section = 0;
        boolean headerSkipped = false;
        for (String raw : lines) {
            String line = raw.trim();
            if (line.startsWith("## ") || line.startsWith("### ")) {
                // 题目标题行，跳过
                continue;
            }
            if (line.isEmpty()) {
                if (section == 0 && content.length() > 0) {
                    content.append('\n');
                } else if (section == 1 && answer.length() > 0) {
                    answer.append('\n');
                }
                continue;
            }
            // 元数据行 [type=.., difficulty=..]
            Matcher m = META_LINE.matcher(line);
            if (m.matches()) {
                if (m.group(1) != null) {
                    type = m.group(1);
                }
                if (m.group(2) != null) {
                    difficulty = Integer.parseInt(m.group(2));
                }
                headerSkipped = true;
                continue;
            }
            if (line.equalsIgnoreCase("### 标准答案") || line.equalsIgnoreCase("### answer")) {
                section = 1;
                continue;
            }
            if (line.equalsIgnoreCase("### 评分点") || line.equalsIgnoreCase("### scoring points")) {
                section = 2;
                continue;
            }
            if (section == 0) {
                if (content.length() > 0) {
                    content.append('\n');
                }
                content.append(raw.strip());
            } else if (section == 1) {
                if (answer.length() > 0) {
                    answer.append('\n');
                }
                answer.append(raw.strip());
            } else if (section == 2) {
                // 评分点：以 - 或 * 开头
                String pt = line.replaceAll("^[-*]\\s*", "");
                if (!pt.isEmpty()) {
                    scoringPoints.add(pt);
                }
            }
        }

        String contentStr = content.toString().trim();
        if (contentStr.isEmpty()) {
            return null;
        }
        ParsedQuestion pq = new ParsedQuestion();
        pq.setType(type);
        pq.setDifficulty(difficulty);
        pq.setContent(contentStr);
        String ans = answer.toString().trim();
        if (!ans.isEmpty()) {
            pq.setStandardAnswer(ans);
        }
        if (!scoringPoints.isEmpty()) {
            pq.setScoringPoints(scoringPoints);
        }
        return pq;
    }

    // ---------- helpers ----------

    private static String asString(Object o, String def) {
        if (o == null) return def;
        String s = String.valueOf(o).trim();
        return s.isEmpty() ? def : s;
    }

    private static int asInt(Object o, int def) {
        if (o == null) return def;
        if (o instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(o).trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
