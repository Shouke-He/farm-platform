package com.farm.platform.agent;

import com.farm.platform.entity.PlantDisease;
import com.farm.platform.entity.SolarTerm;
import com.farm.platform.mapper.PlantDiseaseMapper;
import com.farm.platform.mapper.SolarTermMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.File;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 知识库索引器：把「病虫害 158 条 + 节气 24 条」变成可语义检索的向量。 */
@Slf4j
@Component
@RequiredArgsConstructor
// 与 AgentConfig / AgentService / FarmTools 同一个开关：关掉时整包消失，不留半个 bean
@ConditionalOnProperty(name = "farm.agent.enabled", havingValue = "true")   // agent.enabled=true 才装配
public class KnowledgeIndexer {

    private final SimpleVectorStore knowledgeStore;
    private final PlantDiseaseMapper diseaseMapper;
    private final SolarTermMapper solarTermMapper;

    @Value("${farm.agent.knowledge-file:}")   // 索引文件路径
    private String knowledgeFile;

    /** 索引是否可用。用它而不是去问 SimpleVectorStore —— 那个类没有 size()， */
    private volatile boolean ready = false;

    /** 启动后建索引：磁盘上有就直接加载，没有才真去跑 embedding。 */
    @EventListener(ApplicationReadyEvent.class)   // 【知识库】
    public void loadOrBuild() {
        File file = indexFile();
        if (file != null && file.isFile()) {
            try {
                knowledgeStore.load(file);
                ready = true;
                log.info("知识库向量索引已从 {} 加载", file.getName());
                return;
            } catch (Exception e) {
                // 文件坏了就当没有：往下走重建，别让一个坏 json 把知识库永久废掉
                log.warn("向量索引加载失败，将重新构建：{}", e.getMessage());
            }
        }
        build();
    }

    /** 真去建索引：读两张表 → 拼成文档 → 灌进向量库 → 落盘 */
    private void build() {
        try {
            List<Document> docs = collectDocuments();
            if (docs.isEmpty()) {
                log.warn("知识库源数据为空，索引未构建");
                return;
            }
            knowledgeStore.add(docs);
            ready = true;
            log.info("知识库索引构建完成：{} 条", docs.size());
            saveIndex();
        } catch (Exception e) {
            // 建索引失败不能拖垮启动：助手退化成"查不到知识库"即可，系统其余部分照常
            log.warn("知识库索引构建失败（助手将无法检索知识库，其余功能不受影响）：{}", e.getMessage());
        }
    }

    /** 把两张表拼成给模型看的文档。 */
    private List<Document> collectDocuments() {
        List<Document> docs = new ArrayList<>();

        for (PlantDisease d : diseaseMapper.selectList(null)) {
            StringBuilder sb = new StringBuilder();
            sb.append("病虫害名称：").append(text(d.getName())).append("。");
            if (notBlank(d.getPlants())) {
                sb.append("易感植物：").append(d.getPlants().trim()).append("。");
            }
            sb.append("症状：").append(text(d.getSymptom())).append("。");
            if (notBlank(d.getCause())) {
                sb.append("发病原因：").append(d.getCause().trim()).append("。");
            }
            if (notBlank(d.getSeason())) {
                sb.append("高发季节：").append(d.getSeason().trim()).append("。");
            }
            if (notBlank(d.getPrevention())) {
                sb.append("防治方法：").append(d.getPrevention().trim()).append("。");
            }
            if (notBlank(d.getDrug())) {
                sb.append("可用药剂：").append(d.getDrug().trim()).append("。");
            }
            docs.add(new Document(sb.toString(),
                    Map.of("type", "disease", "name", text(d.getName()))));
        }

        for (SolarTerm s : solarTermMapper.selectList(null)) {
            StringBuilder sb = new StringBuilder();
            sb.append("节气：").append(text(s.getTermName()));
            if (notBlank(s.getMonthDay())) {
                sb.append("（公历约 ").append(s.getMonthDay().trim()).append("）");
            }
            sb.append("。农事要点：").append(text(s.getAdvice()));
            if (notBlank(s.getProverb())) {
                sb.append("。相关谚语：").append(s.getProverb().trim());
            }
            sb.append("。");
            docs.add(new Document(sb.toString(),
                    Map.of("type", "solar_term", "name", text(s.getTermName()))));
        }
        return docs;
    }

    /** 把建好的索引存到磁盘。失败只告警不抛 —— 顶多下次启动重建，不该影响功能 */
    private void saveIndex() {
        File file = indexFile();
        if (file == null) {
            return;
        }
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                log.warn("索引目录创建失败：{}", parent.getAbsolutePath());
            }
            knowledgeStore.save(file);
            log.info("知识库向量索引已落盘：{}", file.getName());
        } catch (Exception e) {
            log.warn("向量索引落盘失败（下次启动会重建，不影响功能）：{}", e.getMessage());
        }
    }

    /** 语义检索，返回最相关的若干条文档原文。 */
    public List<Document> retrieve(String query, int topK) {
        if (!ready || query == null || query.isBlank()) {
            return List.of();
        }
        try {
            return knowledgeStore.similaritySearch(
                    SearchRequest.builder().query(query).topK(topK).build());
        } catch (Exception e) {
            log.warn("知识库检索失败：{}", e.getMessage());
            return List.of();
        }
    }

    /** 按今天的日期算出当前节气 —— <b>确定性计算，不经过模型</b>。 */
    public SolarTerm currentSolarTerm(LocalDate today) {
        if (today == null) {
            return null;
        }
        List<SolarTerm> terms = solarTermMapper.selectList(null);
        SolarTerm picked = null;
        SolarTerm last = null;
        String todayMd = String.format("%02d-%02d", today.getMonthValue(), today.getDayOfMonth());

        for (SolarTerm t : terms) {
            String md = normalizeMonthDay(t.getMonthDay());
            if (md == null) {
                continue;
            }
            if (last == null || md.compareTo(normalizeMonthDay(last.getMonthDay())) > 0) {
                last = t;
            }
            // MM-DD 定长格式可以直接按字符串比大小（"09-23" < "09-29"）
            if (md.compareTo(todayMd) <= 0 && (picked == null || md.compareTo(normalizeMonthDay(picked.getMonthDay())) > 0)) {
                picked = t;
            }
        }
        return picked != null ? picked : last;
    }

    /** 把 "9-23" / "09-23" / "09月23日" 之类的写法统一成定长的 "09-23"；认不出来返回 null */
    private static String normalizeMonthDay(String raw) {
        if (raw == null) {
            return null;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(\\d{1,2})\\D+(\\d{1,2})").matcher(raw.trim());
        if (!m.find()) {
            return null;
        }
        return String.format("%02d-%02d", Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)));
    }

    /** 索引是否就绪（供日志与排查用） */
    public boolean isReady() {
        return ready;
    }

    private File indexFile() {
        return (knowledgeFile == null || knowledgeFile.isBlank()) ? null : new File(knowledgeFile);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String text(String s) {
        return s == null ? "不详" : s.trim();
    }
}
