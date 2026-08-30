package com.workstudy.agent.jobmatch;

import com.workstudy.entity.Job;
import com.workstudy.mapper.JobMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 岗位向量服务（Agent 1）：
 * 将招聘中岗位的文本（标题+描述+要求+地点+时间）向量化写入 VectorStore，
 * 推荐时以学生画像文本做语义检索（RAG 召回）。
 */
@Service
public class JobVectorService {

    private static final Logger log = LoggerFactory.getLogger(JobVectorService.class);

    private final JobMapper jobMapper;
    private final VectorStore vectorStore;

    public JobVectorService(JobMapper jobMapper, VectorStore vectorStore) {
        this.jobMapper = jobMapper;
        this.vectorStore = vectorStore;
    }

    /**
     * 重建全部在招岗位的向量索引（启动时/管理员手动触发）。
     * 同 id 重复 add 会覆盖，幂等安全。
     */
    public void indexAllJobs() {
        List<Job> jobs = jobMapper.selectByStatus(1);
        List<Document> docs = new ArrayList<>();
        for (Job job : jobs) {
            docs.add(toDocument(job));
        }
        if (!docs.isEmpty()) {
            vectorStore.add(docs);
            log.info("岗位向量索引重建完成，共 {} 条", docs.size());
        }
    }

    /**
     * 语义检索：以查询文本召回最相似的在招岗位。
     */
    public List<Document> search(String queryText, int topK) {
        SearchRequest request = SearchRequest.builder()
                .query(queryText)
                .topK(topK)
                .build();
        return vectorStore.similaritySearch(request);
    }

    /**
     * 索引是否为空（懒加载兜底：首次推荐时若未建索引则自动重建）。
     */
    public boolean isEmpty() {
        return search("任何岗位", 1).isEmpty();
    }

    private Document toDocument(Job job) {
        String text = buildJobText(job);
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("jobId", job.getId());
        metadata.put("departmentId", job.getDepartmentId() == null ? -1L : job.getDepartmentId());
        metadata.put("title", job.getTitle() == null ? "" : job.getTitle());
        return Document.builder()
                .id("job_" + job.getId())
                .text(text)
                .metadata(metadata)
                .build();
    }

    private String buildJobText(Job job) {
        StringBuilder sb = new StringBuilder();
        sb.append("岗位：").append(nvl(job.getTitle()));
        sb.append("；部门：").append(nvl(job.getDepartmentName()));
        sb.append("；工作内容：").append(nvl(job.getDescription()));
        sb.append("；任职要求：").append(nvl(job.getRequirements()));
        sb.append("；薪资：").append(job.getSalary() == null ? "面议" : job.getSalary() + "元/时");
        sb.append("；地点：").append(nvl(job.getLocation()));
        sb.append("；工作时间：").append(nvl(job.getWorkTime()));
        return sb.toString();
    }

    private String nvl(String s) {
        return s == null || s.isBlank() ? "无" : s;
    }
}
