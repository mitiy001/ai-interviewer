package com.aiinterviewer.parser;

import com.aiinterviewer.common.ResultCode;
import com.aiinterviewer.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.tika.Tika;
import org.springframework.stereotype.Component;

import java.io.InputStream;

/**
 * 文件解析器：PDF 走 PDFBox 专用通道（保留排版/表格/多栏顺序），其他格式用 Tika 兜底。
 * <p>
 * 为什么不用 Tika 解析 PDF：
 *   Tika 默认的 parseToString 不启用 sortByPosition，且会把 XHTML 标签混入文本，
 *   导致表格列错乱、多栏阅读顺序混乱、换行丢失。PDFBox 的 PDFTextStripper
 *   可按坐标排序、保留段落结构，对简历类 PDF 准确率显著更高。
 */
@Slf4j
@Component
public class TikaParser {

    private final Tika tika = new Tika();

    /**
     * 解析输入流为纯文本
     *
     * @param filename 原始文件名（用于判定格式 + 错误提示）
     * @param in       输入流
     * @return 解析后的纯文本
     */
    public String parse(String filename, InputStream in) {
        if (filename == null) {
            return parseWithTika(filename, in);
        }
        String lower = filename.toLowerCase();
        // PDF 走 PDFBox 专用通道，避免 Tika 的 XHTML 标签和排序问题
        if (lower.endsWith(".pdf")) {
            return parsePdfWithPdfBox(filename, in);
        }
        return parseWithTika(filename, in);
    }

    /**
     * PDFBox 专用 PDF 解析：按坐标排序 + 保留段落结构 + 保留换行。
     * <p>
     * 配置说明：
     *   - setSortByPosition(true)：按文本在页面中的实际坐标位置排序，
     *     解决 PDF 内文本对象乱序问题（扫描件/OCR 后的 PDF 尤其重要）
     *   - setAddMoreFormatting(true)：在段落间添加额外换行，保留段落结构
     *   - 默认保留页面内换行，避免段落被压成一行
     */
    private String parsePdfWithPdfBox(String filename, InputStream in) {
        try (PDDocument document = PDDocument.load(in)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            stripper.setAddMoreFormatting(true);
            // 段落分隔：页面间用双换行，段落间保留原有换行
            stripper.setParagraphStart("\n");
            stripper.setParagraphEnd("\n");
            stripper.setPageStart("\n\n");
            stripper.setPageEnd("");

            String text = stripper.getText(document);
            if (text == null) {
                text = "";
            }
            text = normalizeWhitespace(text);
            log.info("PDFBox 解析完成 filename={} pages={} length={}",
                    filename, document.getNumberOfPages(), text.length());
            return text;
        } catch (Exception e) {
            log.warn("PDFBox 解析失败 filename={}，回退到 Tika: {}",
                    filename, e.getMessage());
            // PDFBox 失败时回退到 Tika（可能是加密 PDF 或损坏文件）
            return parseWithTika(filename, in);
        }
    }

    /**
     * Tika 兜底解析：用于 Word/MD/TXT 等非 PDF 格式，或 PDFBox 失败时回退。
     */
    private String parseWithTika(String filename, InputStream in) {
        try {
            String text = tika.parseToString(in);
            if (text == null) {
                text = "";
            }
            text = normalizeWhitespace(text);
            log.info("Tika 解析完成 filename={} length={}", filename, text.length());
            return text;
        } catch (Exception e) {
            log.error("Tika 解析失败 filename={}", filename, e);
            throw new BusinessException(ResultCode.BUSINESS_ERROR,
                    "文件解析失败: " + e.getMessage());
        }
    }

    /**
     * 规范化空白字符：去除尾部空白、合并连续空行（>2 个换行压缩为 2 个），
     * 保留单个换行和段落结构。
     */
    private String normalizeWhitespace(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        // 去除每行首尾的空白（保留换行符）
        String[] lines = text.split("\n", -1);
        StringBuilder sb = new StringBuilder(text.length());
        int blankCount = 0;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                blankCount++;
                // 连续空行最多保留 1 个（即段落间双换行）
                if (blankCount <= 1) {
                    sb.append('\n');
                }
            } else {
                blankCount = 0;
                sb.append(trimmed).append('\n');
            }
        }
        return sb.toString().trim();
    }
}
