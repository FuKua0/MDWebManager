package com.markdowonweb.Controller;

import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpSession;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;

@Controller
public class MarkdownController {

    private static final Logger logger = LoggerFactory.getLogger(MarkdownController.class);

    @Value("${markdown.dir}")
    private String MARKDOWN_DIR;

    private final Map<String, String> markdownCache = new HashMap<>();
    private Map<String, List<String>> directoryStructureCache = new HashMap<>();
    private long lastUpdateTime = 0;
    private static final long UPDATE_INTERVAL = 60 * 1000; // 1 分钟更新一次文件列表

    @GetMapping("/markdown-list")
    public String listMarkdownFiles(HttpSession session, Model model, @RequestParam(required = false) String search) {
        String username = (String) session.getAttribute("username");
        if (username == null) {
            return "redirect:/login";
        }

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastUpdateTime > UPDATE_INTERVAL) {
            directoryStructureCache = new HashMap<>();
            File dir = new File(MARKDOWN_DIR);
            buildDirectoryStructure(dir, "");
            lastUpdateTime = currentTime;
        }

        Map<String, List<String>> filteredStructure = directoryStructureCache;
        if (search != null && !search.isEmpty()) {
            filteredStructure = new HashMap<>();
            String lowerCaseSearch = search.toLowerCase();
            for (Map.Entry<String, List<String>> entry : directoryStructureCache.entrySet()) {
                String directory = entry.getKey();
                List<String> files = entry.getValue();
                List<String> filteredFiles = new ArrayList<>();
                for (String file : files) {
                    if (file.toLowerCase().contains(lowerCaseSearch)) {
                        filteredFiles.add(file);
                    }
                }
                if (!filteredFiles.isEmpty()) {
                    filteredStructure.put(directory, filteredFiles);
                }
            }
        }

        // 对目录结构进行排序，确保目录在前
        List<Map.Entry<String, List<String>>> sortedEntries = new ArrayList<>(filteredStructure.entrySet());
        sortedEntries.sort((e1, e2) -> {
            if (e1.getKey().isEmpty() && !e2.getKey().isEmpty()) {
                return -1; // 根目录排在前面
            } else if (!e1.getKey().isEmpty() && e2.getKey().isEmpty()) {
                return 1;
            } else {
                return e1.getKey().compareTo(e2.getKey());
            }
        });

        Map<String, List<String>> sortedStructure = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : sortedEntries) {
            sortedStructure.put(entry.getKey(), entry.getValue());
        }

        model.addAttribute("directoryStructure", sortedStructure);
        return "markdown-list";
    }

    private void buildDirectoryStructure(File dir, String parentPath) {
        if (dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                List<String> currentFiles = new ArrayList<>();
                for (File file : files) {
                    if (file.isDirectory()) {
                        String newParentPath = parentPath.isEmpty() ? file.getName() : parentPath + "/" + file.getName();
                        buildDirectoryStructure(file, newParentPath);
                    } else if (file.getName().endsWith(".md")) {
                        currentFiles.add(file.getName());
                    }
                }
                if (!currentFiles.isEmpty()) {
                    directoryStructureCache.put(parentPath, currentFiles);
                }
            }
        }
    }

    @GetMapping("/markdown")
    public String markdownPage(HttpSession session, @RequestParam String fileName, Model model) {
        String username = (String) session.getAttribute("username");
        if (username == null) {
            return "redirect:/login";
        }

        //验证文件名是否合法
        if (!fileName.endsWith(".md")) {
            // 处理非法文件名
            return new String("redirect:/markdown-list");
        }

        //防止目录遍历攻击
        java.nio.file.Path basePath = java.nio.file.Paths.get(MARKDOWN_DIR).normalize();
        java.nio.file.Path filePath = basePath.resolve(fileName).normalize();
        if (!filePath.startsWith(basePath)) {
            // 处理非法路径
            return new String("redirect:/markdown-list");
        }

        logger.info("Received fileName: {}", fileName);
        String htmlContent = markdownCache.get(fileName);
        if (htmlContent == null) {
            File file = new File(MARKDOWN_DIR, fileName);
            logger.info("Trying to read file: {}", file.getAbsolutePath());
            try {
                StringBuilder content = new StringBuilder();
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(new FileInputStream(file), "UTF-8"));
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line).append("\n");
                }
                reader.close();

                Parser parser = Parser.builder().build();
                Node document = parser.parse(content.toString());
                HtmlRenderer renderer = HtmlRenderer.builder().build();
                htmlContent = renderer.render(document);
                markdownCache.put(fileName, htmlContent);
            } catch (IOException e) {
                logger.error("Error reading file: {}", file.getAbsolutePath(), e);
                e.printStackTrace();
            }
        }

        model.addAttribute("markdownContent", htmlContent);
        model.addAttribute("filename", fileName);
        return "markdown";
    }
}