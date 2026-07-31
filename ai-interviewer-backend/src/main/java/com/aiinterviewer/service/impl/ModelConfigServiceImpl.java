package com.aiinterviewer.service.impl;

import com.aiinterviewer.common.ResultCode;
import com.aiinterviewer.common.UserContext;
import com.aiinterviewer.dto.req.ModelConfigReq;
import com.aiinterviewer.dto.resp.ModelConfigResp;
import com.aiinterviewer.dto.resp.ModelTestResp;
import com.aiinterviewer.entity.ModelConfig;
import com.aiinterviewer.exception.BusinessException;
import com.aiinterviewer.graph.ChatClientFactory;
import com.aiinterviewer.mapper.ModelConfigMapper;
import com.aiinterviewer.service.ModelConfigService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * 模型配置 Service 实现
 * <p>
 * 关键点：
 * 1. 同一 user 只能有一个 is_active=1，激活时先把该 user 所有配置置 0
 * 2. api_key 不进日志（不打印含 apiKey 的实体）
 * 3. 对外返回脱敏（apiKeyMasked）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModelConfigServiceImpl implements ModelConfigService {

    private final ModelConfigMapper modelConfigMapper;
    private final ChatClientFactory chatClientFactory;

    /** 连通测试专用线程池，避免阻塞 tomcat 主线程池 */
    private final ExecutorService testPool = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "model-test");
        t.setDaemon(true);
        return t;
    });

    /** 连通测试超时时间（秒） */
    private static final long TEST_TIMEOUT_SECONDS = 20;
    /** 连通测试用的 prompt */
    private static final String TEST_PROMPT = "请只回复一个词：pong";

    @Override
    public List<ModelConfigResp> list() {
        LambdaQueryWrapper<ModelConfig> qw = new LambdaQueryWrapper<>();
        qw.eq(ModelConfig::getUserId, UserContext.getUserId())
                .orderByDesc(ModelConfig::getIsActive)
                .orderByDesc(ModelConfig::getCreatedAt);
        return modelConfigMapper.selectList(qw).stream()
                .map(ModelConfigServiceImpl::toResp)
                .collect(Collectors.toList());
    }

    @Override
    public ModelConfigResp get(Long id) {
        ModelConfig entity = mustGetOwned(id);
        return toResp(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(ModelConfigReq req) {
        ModelConfig entity = new ModelConfig();
        entity.setUserId(UserContext.getUserId());
        copyReqToEntity(req, entity);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        // 默认未激活
        if (entity.getIsActive() == null) {
            entity.setIsActive(0);
        }
        modelConfigMapper.insert(entity);
        // 若新增即激活，取消其他
        if (Integer.valueOf(1).equals(entity.getIsActive())) {
            deactivateOthers(entity.getId());
        }
        log.info("create model_config id={} name={} provider={}", entity.getId(), entity.getName(), entity.getProvider());
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, ModelConfigReq req) {
        ModelConfig entity = mustGetOwned(id);
        copyReqToEntity(req, entity);
        entity.setUpdatedAt(LocalDateTime.now());
        if (entity.getIsActive() == null) {
            entity.setIsActive(0);
        }
        modelConfigMapper.updateById(entity);
        if (Integer.valueOf(1).equals(entity.getIsActive())) {
            deactivateOthers(entity.getId());
        }
        log.info("update model_config id={} name={} provider={}", entity.getId(), entity.getName(), entity.getProvider());
    }

    @Override
    public void delete(Long id) {
        ModelConfig entity = mustGetOwned(id);
        modelConfigMapper.deleteById(id);
        log.info("delete model_config id={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void activate(Long id) {
        mustGetOwned(id);
        LambdaUpdateWrapper<ModelConfig> upd = new LambdaUpdateWrapper<>();
        upd.eq(ModelConfig::getId, id)
                .eq(ModelConfig::getUserId, UserContext.getUserId())
                .set(ModelConfig::getIsActive, 1)
                .set(ModelConfig::getUpdatedAt, LocalDateTime.now());
        modelConfigMapper.update(null, upd);
        deactivateOthers(id);
        log.info("activate model_config id={}", id);
    }

    @Override
    public ModelConfig getActiveRaw() {
        LambdaQueryWrapper<ModelConfig> qw = new LambdaQueryWrapper<>();
        qw.eq(ModelConfig::getUserId, UserContext.getUserId())
                .eq(ModelConfig::getIsActive, 1)
                .last("LIMIT 1");
        return modelConfigMapper.selectOne(qw);
    }

    @Override
    public ModelTestResp test(Long id) {
        ModelConfig entity = mustGetOwned(id);
        return doTest(entity);
    }

    @Override
    public ModelTestResp test(ModelConfigReq req) {
        ModelConfig entity = new ModelConfig();
        copyReqToEntity(req, entity);
        entity.setUserId(UserContext.getUserId());

        // 编辑场景：req 带 id 且 apiKey 为空或脱敏占位符时，回查数据库补全真实密钥
        // 否则会在 ChatClientFactory.validate() 抛 "api_key 未配置"
        if (req.getId() != null && !isRealSecret(entity.getApiKey())) {
            ModelConfig saved = mustGetOwned(req.getId());
            if (!isRealSecret(entity.getApiKey())) {
                entity.setApiKey(saved.getApiKey());
            }
            if (!isRealSecret(entity.getTtsApiKey())) {
                entity.setTtsApiKey(saved.getTtsApiKey());
            }
        }

        // 新建场景：apiKey 必须是真实密钥
        if (req.getId() == null && !isRealSecret(entity.getApiKey())) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "测试时 api_key 必填");
        }
        return doTest(entity);
    }

    /** 实际执行 LLM 调用，带超时保护 */
    private ModelTestResp doTest(ModelConfig config) {
        long start = System.currentTimeMillis();
        try {
            ChatModel model = chatClientFactory.createChatModel(config);
            // 提交到独立线程池，避免 tomcat 线程被长时间占用
            String reply = testPool.submit(() -> model.call(new Prompt(TEST_PROMPT)).getResult().getOutput().getText())
                    .get(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            long latency = System.currentTimeMillis() - start;
            if (reply == null) reply = "";
            log.info("模型连通测试成功 configId={} model={} latencyMs={}ms replyLen={}",
                    config.getId(), config.getModel(), latency, reply.length());
            return ModelTestResp.ok(reply, latency);
        } catch (TimeoutException e) {
            long latency = System.currentTimeMillis() - start;
            String msg = "请求超时（>" + TEST_TIMEOUT_SECONDS + "s），请检查 endpoint 是否可达";
            log.warn("模型连通测试超时 configId={} model={}", config.getId(), config.getModel());
            return ModelTestResp.fail(msg, latency);
        } catch (java.util.concurrent.ExecutionException e) {
            long latency = System.currentTimeMillis() - start;
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            log.warn("模型连通测试失败 configId={} model={}: {}",
                    config.getId(), config.getModel(), cause.getMessage());
            return ModelTestResp.fail(extractErrorMessage(cause), latency);
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            log.warn("模型连通测试失败 configId={} model={}: {}",
                    config.getId(), config.getModel(), e.getMessage());
            return ModelTestResp.fail(extractErrorMessage(e), latency);
        }
    }

    /** 从异常中提取用户可读的错误信息 */
    private String extractErrorMessage(Throwable e) {
        String msg = e.getMessage();
        if (msg == null) return "未知错误: " + e.getClass().getSimpleName();
        // 截断过长的 stack 信息
        int newline = msg.indexOf('\n');
        if (newline > 0) msg = msg.substring(0, newline);
        if (msg.length() > 200) msg = msg.substring(0, 200) + "...";
        return msg;
    }

    // ---------- private ----------

    private ModelConfig mustGetOwned(Long id) {
        ModelConfig entity = modelConfigMapper.selectById(id);
        if (entity == null || !UserContext.getUserId().equals(entity.getUserId())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "模型配置不存在");
        }
        return entity;
    }

    /** 把当前用户下除 excludeId 外的所有配置置为未激活 */
    private void deactivateOthers(Long excludeId) {
        LambdaUpdateWrapper<ModelConfig> upd = new LambdaUpdateWrapper<>();
        upd.eq(ModelConfig::getUserId, UserContext.getUserId())
                .ne(ModelConfig::getId, excludeId)
                .eq(ModelConfig::getIsActive, 1)
                .set(ModelConfig::getIsActive, 0)
                .set(ModelConfig::getUpdatedAt, LocalDateTime.now());
        modelConfigMapper.update(null, upd);
    }

    private static void copyReqToEntity(ModelConfigReq req, ModelConfig entity) {
        entity.setName(req.getName());
        entity.setProvider(req.getProvider());
        // apiKey 为空或为脱敏回显值（含 ****）时保留原值
        if (isRealSecret(req.getApiKey())) {
            entity.setApiKey(req.getApiKey());
        }
        entity.setModel(req.getModel());
        entity.setEndpoint(req.getEndpoint());
        entity.setJudgeModel(req.getJudgeModel());
        entity.setJudgeEndpoint(req.getJudgeEndpoint());
        entity.setTtsEndpoint(req.getTtsEndpoint());
        // ttsApiKey 同理
        if (isRealSecret(req.getTtsApiKey())) {
            entity.setTtsApiKey(req.getTtsApiKey());
        }
        entity.setTtsModel(req.getTtsModel());
        entity.setTtsVoice(req.getTtsVoice());
        entity.setIsActive(req.getIsActive());
    }

    /** 判断是否为真实密钥：非空、非纯空白、且不含脱敏占位符 **** */
    private static boolean isRealSecret(String s) {
        return s != null && !s.isBlank() && !s.contains("****");
    }

    /** 脱敏：apiKey 保留前 4 + 后 4，中间打码 */
    private static ModelConfigResp toResp(ModelConfig entity) {
        ModelConfigResp resp = new ModelConfigResp();
        resp.setId(entity.getId());
        resp.setUserId(entity.getUserId());
        resp.setName(entity.getName());
        resp.setProvider(entity.getProvider());
        resp.setModel(entity.getModel());
        resp.setEndpoint(entity.getEndpoint());
        resp.setJudgeModel(entity.getJudgeModel());
        resp.setJudgeEndpoint(entity.getJudgeEndpoint());
        resp.setTtsEndpoint(entity.getTtsEndpoint());
        resp.setTtsModel(entity.getTtsModel());
        resp.setTtsVoice(entity.getTtsVoice());
        resp.setTtsApiKeyMasked(maskApiKey(entity.getTtsApiKey()));
        resp.setApiKeyMasked(maskApiKey(entity.getApiKey()));
        resp.setIsActive(entity.getIsActive());
        resp.setCreatedAt(entity.getCreatedAt());
        resp.setUpdatedAt(entity.getUpdatedAt());
        return resp;
    }

    private static String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.isEmpty()) {
            return "";
        }
        int len = apiKey.length();
        if (len <= 8) {
            return "****";
        }
        return apiKey.substring(0, 4) + "****" + apiKey.substring(len - 4);
    }
}
