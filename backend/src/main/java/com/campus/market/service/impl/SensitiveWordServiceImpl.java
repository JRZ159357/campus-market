// 文件路径: src/main/java/com/campus/market/service/impl/SensitiveWordServiceImpl.java
package com.campus.market.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.market.common.BusinessException;
import com.campus.market.common.OpLogModule;
import com.campus.market.common.ResultCode;
import com.campus.market.common.SensitiveCheckResult;
import com.campus.market.common.SensitiveWordLevel;
import com.campus.market.common.SensitiveWordType;
import com.campus.market.common.annotation.OpLog;
import com.campus.market.dto.SensitiveWordBatchDTO;
import com.campus.market.dto.SensitiveWordQueryDTO;
import com.campus.market.dto.SensitiveWordSaveDTO;
import com.campus.market.entity.SensitiveWord;
import com.campus.market.mapper.SensitiveWordMapper;
import com.campus.market.service.SensitiveWordService;
import com.campus.market.vo.SensitiveWordBatchVO;
import com.campus.market.vo.SensitiveWordVO;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 敏感词服务实现：内存快照 + 写时整体替换 + 统一检测入口。
 * <p>
 * <b>为什么用内存而不是每次查 Redis / 查库</b>：
 * 检测发生在「发布商品、提交评价、发一条聊天消息」这三条高频路径上。
 * 聊天消息是全站最高频的写操作，如果每条消息都去 Redis 取一次全量词表
 * （假设 1000 个词、20KB），单机 QPS 上千时这部分网络开销会明显压到 RT 上，
 * 而且 Redis 抖动会直接让聊天不可用。词表是「读极多、写极少」的配置数据
 * （运营一天改不了几次），放进堆内存是最合适的——一次读取，全部请求复用。
 * <p>
 * <b>为什么不加锁</b>：快照对象本身不可变（{@link WordSnapshot} 的字段全是
 * final 集合），刷新时整体替换 {@code volatile} 引用。读线程要么看到旧快照
 * 要么看到新快照，都不会看到「改了一半」的中间态。
 * 代价是刷新瞬间可能还按旧词典拦截几百毫秒——这个偏差对业务无影响，
 * 比给每次检测加读锁划算得多。
 * <p>
 * <b>为什么刷新放在事务提交之后</b>：如果在事务内刷新，读到的是本事务
 * 未提交的数据；一旦事务回滚（比如后续步骤抛异常），内存里就留下了一个
 * 数据库里并不存在的词，且要等下一次写操作才会被纠正。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SensitiveWordServiceImpl extends ServiceImpl<SensitiveWordMapper, SensitiveWord>
        implements SensitiveWordService {

    /** 每页默认条数 */
    private static final long DEFAULT_PAGE_SIZE = 20L;

    /** 每页最大条数，防止 pageSize=999999 一次拉全表 */
    private static final long MAX_PAGE_SIZE = 200L;

    /** 单次批量导入的最大行数：超过这个量说明是在灌数据，应该走 SQL 脚本 */
    private static final int MAX_BATCH_LINES = 1000;

    /** 单词最大长度，与表字段 varchar(50) 对齐 */
    private static final int MAX_WORD_LENGTH = 50;

    /** 命中提示里最多列出多少个词，避免一段长文命中几十个词时提示框被撑爆 */
    private static final int MAX_HINT_WORDS = 10;

    /** 回显给前端的跳过明细上限 */
    private static final int MAX_ECHO_LINES = 50;

    /**
     * 归一化时剔除的分隔符。
     * <p>
     * 只剔除英文标点与常见「插字符」而不剔除中文标点：
     * 中文标点承担断句语义，去掉后「废物」和「他真是个废物」之外的
     * 跨句拼接会产生误判（例如「这笔生意做不成。骗子太多了」不该被算成
     * 「骗子」以外的组合）。英文符号则几乎不会出现在正常中文句子里，
     * 去掉它们纯粹是为了对付「加*微*信」这类规避写法。
     */
    private static final String SEPARATORS = "*-_.·|/\\~`'\"#@+^&%$!?()[]{}<>";

    /**
     * 当前生效的词表快照。volatile 保证「整体替换」对其他线程立即可见。
     */
    private volatile WordSnapshot snapshot = WordSnapshot.EMPTY;

    /**
     * 启动时加载词表。
     * <p>
     * 加载失败不允许影响应用启动：表还没建、数据库正在迁移、多实例里
     * 某个实例启动早于 SQL 执行，这些情况下应用仍然应该能起来
     * （敏感词为空 = 不过滤，业务功能不受损），而不是启动即崩。
     */
    @PostConstruct
    public void init() {
        try {
            int count = reload();
            log.info("敏感词词典加载完成，共 {} 个启用词", count);
        } catch (Exception e) {
            log.warn("敏感词词典加载失败，本次启动将不做敏感词过滤：{}", e.getMessage());
        }
    }

    // ============================================================
    // 一、检测
    // ============================================================

    @Override
    public SensitiveCheckResult check(String... texts) {
        if (texts == null || texts.length == 0) {
            return SensitiveCheckResult.pass();
        }
        WordSnapshot current = this.snapshot;
        if (current.isEmpty()) {
            return SensitiveCheckResult.pass();
        }
        // 多段文本各自归一化后拼接检测：直接用分隔符隔开可以避免
        // 「标题末尾 + 描述开头」跨字段拼出一个原本不存在的词
        Set<String> blockSet = new LinkedHashSet<>();
        Set<String> warnSet = new LinkedHashSet<>();
        for (String text : texts) {
            if (!StringUtils.hasText(text)) {
                continue;
            }
            String normalized = normalize(text);
            if (normalized.isEmpty()) {
                continue;
            }
            current.match(normalized, blockSet, warnSet);
        }
        if (blockSet.isEmpty() && warnSet.isEmpty()) {
            return SensitiveCheckResult.pass();
        }
        return SensitiveCheckResult.of(
                cap(blockSet), cap(warnSet));
    }

    /**
     * 截断到提示上限，避免一次命中上百个词时把文案撑成一段长文。
     */
    private List<String> cap(Set<String> words) {
        if (words.isEmpty()) {
            return Collections.emptyList();
        }
        return words.stream().limit(MAX_HINT_WORDS).collect(Collectors.toList());
    }

    /**
     * 文本归一化：全角转半角 → 去空白与规避符号 → 转小写。
     * <p>
     * 这一步是「加 微 信」「加*微信」「加微 信」都能拦住的关键。
     * 反过来，它也会带来误判的可能（把两个正常词拼成敏感词），
     * 所以只剔除非中文符号，中文标点保留作断句。
     */
    private String normalize(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            // 零宽字符：从网页复制文本时经常被带进来，肉眼不可见但能让匹配失效
            if (c == '\u200B' || c == '\u200C' || c == '\u200D' || c == '\uFEFF') {
                continue;
            }
            // 全角 ASCII 转半角
            if (c >= '\uFF01' && c <= '\uFF5E') {
                c = (char) (c - 0xFEE0);
            } else if (c == '\u3000') {
                // 全角空格
                continue;
            }
            if (Character.isWhitespace(c) || SEPARATORS.indexOf(c) >= 0) {
                continue;
            }
            sb.append(Character.toLowerCase(c));
        }
        return sb.toString();
    }

    // ============================================================
    // 二、词典维护
    // ============================================================

    @Override
    public IPage<SensitiveWordVO> page(SensitiveWordQueryDTO query) {
        SensitiveWordQueryDTO q = query == null ? new SensitiveWordQueryDTO() : query;
        long pageNum = q.getPageNum() == null || q.getPageNum() < 1 ? 1L : q.getPageNum();
        long pageSize = q.getPageSize() == null || q.getPageSize() < 1
                ? DEFAULT_PAGE_SIZE : Math.min(q.getPageSize(), MAX_PAGE_SIZE);

        LambdaQueryWrapper<SensitiveWord> wrapper = Wrappers.<SensitiveWord>lambdaQuery()
                .eq(q.getType() != null, SensitiveWord::getType, q.getType())
                .eq(q.getStatus() != null, SensitiveWord::getStatus, q.getStatus())
                .like(StringUtils.hasText(q.getKeyword()), SensitiveWord::getWord, q.getKeyword())
                // 按创建时间倒序：运营刚加的词排在最前，方便确认「刚才那条加进去了没」；
                // id 倒序作为同一秒内多次插入的稳定次序
                .orderByDesc(SensitiveWord::getCreateTime)
                .orderByDesc(SensitiveWord::getId);

        Page<SensitiveWord> page = this.page(new Page<>(pageNum, pageSize), wrapper);
        return page.convert(this::toVO);
    }

    @Override
    @OpLog(module = OpLogModule.SENSITIVE_WORD, action = OpLogModule.ACTION_CREATE,
            desc = "新增敏感词")
    @Transactional(rollbackFor = Exception.class)
    public Long create(SensitiveWordSaveDTO dto) {
        String word = normalizeInputWord(dto.getWord());
        ensureWordAbsent(word, null);

        SensitiveWord entity = new SensitiveWord();
        entity.setWord(word);
        entity.setType(validType(dto.getType()));
        entity.setLevel(validLevel(dto.getLevel()));
        entity.setStatus(dto.getStatus() == null ? 1 : (dto.getStatus() == 0 ? 0 : 1));
        try {
            this.save(entity);
        } catch (DuplicateKeyException e) {
            // 并发下两次提交同时通过了上面的存在性检查，唯一索引兜底
            throw new BusinessException("敏感词「" + word + "」已存在");
        }
        scheduleReload();
        log.info("新增敏感词: id={}, word={}, type={}, level={}",
                entity.getId(), word, entity.getType(), entity.getLevel());
        return entity.getId();
    }

    @Override
    @OpLog(module = OpLogModule.SENSITIVE_WORD, action = OpLogModule.ACTION_UPDATE, targetIndex = 1,
            desc = "编辑敏感词")
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, SensitiveWordSaveDTO dto) {
        SensitiveWord exists = this.getById(id);
        if (exists == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "敏感词不存在或已被删除");
        }
        String word = normalizeInputWord(dto.getWord());
        ensureWordAbsent(word, id);

        SensitiveWord update = new SensitiveWord();
        update.setId(id);
        update.setWord(word);
        update.setType(validType(dto.getType()));
        update.setLevel(validLevel(dto.getLevel()));
        // 编辑时不传状态表示「保持原状态」，而不是偷偷改成启用
        if (dto.getStatus() != null) {
            update.setStatus(dto.getStatus() == 0 ? 0 : 1);
        }
        try {
            this.updateById(update);
        } catch (DuplicateKeyException e) {
            throw new BusinessException("敏感词「" + word + "」已存在");
        }
        scheduleReload();
        log.info("修改敏感词: id={}, word={}", id, word);
    }

    @Override
    @OpLog(module = OpLogModule.SENSITIVE_WORD, action = OpLogModule.ACTION_DELETE, targetIndex = 1,
            desc = "删除敏感词")
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SensitiveWord exists = this.getById(id);
        if (exists == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "敏感词不存在或已被删除");
        }
        // 物理删除：词表是配置数据，逻辑删除会让 uk_word 在重新录入
        // 同一个词时报唯一键冲突，运维遇到会以为「删不掉」
        baseMapper.deleteById(id);
        scheduleReload();
        log.info("删除敏感词: id={}, word={}", id, exists.getWord());
    }

    @Override
    @OpLog(module = OpLogModule.SENSITIVE_WORD, action = OpLogModule.ACTION_STATUS, targetIndex = 1,
            desc = "启用/禁用敏感词")
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "状态只能是 0（禁用）或 1（启用）");
        }
        SensitiveWord exists = this.getById(id);
        if (exists == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "敏感词不存在或已被删除");
        }
        if (Integer.valueOf(status).equals(exists.getStatus())) {
            throw new BusinessException(status == 1 ? "该敏感词已是启用状态" : "该敏感词已是禁用状态");
        }
        SensitiveWord update = new SensitiveWord();
        update.setId(id);
        update.setStatus(status);
        this.updateById(update);
        scheduleReload();
        log.info("{}敏感词: id={}, word={}", status == 1 ? "启用" : "禁用", id, exists.getWord());
    }

    @Override
    @OpLog(module = OpLogModule.SENSITIVE_WORD, action = OpLogModule.ACTION_BATCH_IMPORT,
            desc = "批量导入敏感词")
    @Transactional(rollbackFor = Exception.class)
    public SensitiveWordBatchVO batchCreate(SensitiveWordBatchDTO dto) {
        int type = validType(dto.getType());
        int level = validLevel(dto.getLevel());
        int status = dto.getStatus() == null ? 1 : (dto.getStatus() == 0 ? 0 : 1);

        // 1) 拆行 + 去空行 + 保序去重
        Set<String> candidates = new LinkedHashSet<>();
        for (String line : dto.getText().split("\\r?\\n")) {
            String word = line.trim();
            if (!word.isEmpty()) {
                candidates.add(word);
            }
        }
        if (candidates.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "没有识别到任何敏感词，请一行输入一个词");
        }
        if (candidates.size() > MAX_BATCH_LINES) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "单次最多导入 " + MAX_BATCH_LINES + " 个词，当前识别到 " + candidates.size() + " 个");
        }

        // 2) 拆成「合法」与「超长」两组。超长的词即使入库也永远匹配不到
        //    （业务字段长度有限），直接跳过并回显，比截断后静默入库更诚实。
        //    注意这里<b>不能</b>复用 normalizeInputWord：它遇到超长词会抛异常，
        //    批量场景下抛异常等于「一个坏词废掉整批」，与「跳过并回显」的设计相反。
        List<String> valid = new ArrayList<>();
        List<String> invalid = new ArrayList<>();
        for (String word : candidates) {
            String normalized = word.trim().replaceAll("\\s+", " ");
            if (normalized.isEmpty() || normalized.length() > MAX_WORD_LENGTH) {
                invalid.add(word);
            } else {
                valid.add(normalized);
            }
        }

        // 3) 一次性查出已存在的词，而不是逐条 selectCount
        //    （1000 行词表逐条查就是 1000 次往返）
        Set<String> existing = valid.isEmpty() ? Collections.emptySet()
                : this.lambdaQuery()
                        .select(SensitiveWord::getWord)
                        .in(SensitiveWord::getWord, valid)
                        .list().stream()
                        .map(SensitiveWord::getWord)
                        .collect(Collectors.toSet());
        List<String> toInsert = valid.stream()
                .filter(w -> !existing.contains(w))
                .collect(Collectors.toList());

        int inserted = insertBatch(toInsert, type, level, status);
        int duplicated = candidates.size() - invalid.size() - inserted;

        SensitiveWordBatchVO result = new SensitiveWordBatchVO();
        result.setTotal(candidates.size());
        result.setInserted(inserted);
        result.setDuplicated(duplicated);
        result.setInvalid(invalid.size());
        result.setDuplicatedWords(valid.stream()
                .filter(existing::contains).limit(MAX_ECHO_LINES).collect(Collectors.toList()));
        result.setInvalidWords(invalid.stream().limit(MAX_ECHO_LINES).collect(Collectors.toList()));

        if (inserted > 0) {
            scheduleReload();
        }
        log.info("批量导入敏感词: 识别 {} 条，新增 {} 条，重复 {} 条，非法 {} 条",
                candidates.size(), inserted, duplicated, invalid.size());
        return result;
    }

    /**
     * 批量写入。正常路径走 JDBC 批量；并发下若撞上唯一索引，
     * 降级成逐条插入以精确统计成功条数——批量语句失败时整批都不生效，
     * 直接回报「全部重复」是错的。
     */
    private int insertBatch(List<String> words, int type, int level, int status) {
        if (words.isEmpty()) {
            return 0;
        }
        List<SensitiveWord> entities = words.stream().map(w -> {
            SensitiveWord entity = new SensitiveWord();
            entity.setWord(w);
            entity.setType(type);
            entity.setLevel(level);
            entity.setStatus(status);
            return entity;
        }).collect(Collectors.toList());
        try {
            this.saveBatch(entities, 200);
            return entities.size();
        } catch (DuplicateKeyException e) {
            log.warn("批量插入敏感词撞到重复键，降级为逐条插入：{}", e.getMessage());
            int ok = 0;
            for (SensitiveWord entity : entities) {
                try {
                    this.save(entity);
                    ok++;
                } catch (DuplicateKeyException ignore) {
                    // 并发插入的重复词，跳过即可
                }
            }
            return ok;
        }
    }

    @Override
    public List<String> listEnabledWords() {
        return this.lambdaQuery()
                .select(SensitiveWord::getWord)
                .eq(SensitiveWord::getStatus, 1)
                .orderByAsc(SensitiveWord::getId)
                .list().stream()
                .map(SensitiveWord::getWord)
                .collect(Collectors.toList());
    }

    @Override
    public int reload() {
        List<SensitiveWord> words = this.lambdaQuery()
                .select(SensitiveWord::getWord, SensitiveWord::getLevel)
                .eq(SensitiveWord::getStatus, 1)
                .list();
        List<WordEntry> entries = new ArrayList<>(words.size());
        for (SensitiveWord word : words) {
            String normalized = normalize(word.getWord());
            if (normalized.isEmpty()) {
                // 全都是分隔符的词没有意义，留着只会拖慢每次检测
                continue;
            }
            entries.add(new WordEntry(word.getWord(), normalized,
                    word.getLevel() == null ? SensitiveWordLevel.WARN : word.getLevel()));
        }
        // 长词优先：先匹配「兼职日入」再匹配「兼职」，命中提示更准确
        entries.sort((a, b) -> b.normalized.length() - a.normalized.length());
        this.snapshot = new WordSnapshot(entries);
        return entries.size();
    }

    /**
     * 刷新词典。事务内推迟到提交之后，避免回滚后内存里留下不存在的词。
     */
    private void scheduleReload() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    safeReload();
                }
            });
            return;
        }
        safeReload();
    }

    private void safeReload() {
        try {
            reload();
        } catch (Exception e) {
            // 刷新失败不影响本次写入：数据库已是对的，只是内存暂时滞后，
            // 下一次写操作或重启会纠正。这里绝不能把异常抛回去让主业务失败。
            log.warn("敏感词词典刷新失败：{}", e.getMessage());
        }
    }

    // ============================================================
    // 三、内部工具
    // ============================================================

    /**
     * 入参词清洗：去首尾空白、把连续空白压成一个（词内部的空格保留，
     * 因为「加 微信」这种带空格的词本身也是合法词条）。
     */
    private String normalizeInputWord(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "敏感词不能为空");
        }
        String word = raw.trim().replaceAll("\\s+", " ");
        if (word.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "敏感词不能为空");
        }
        if (word.length() > MAX_WORD_LENGTH) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "敏感词不能超过 " + MAX_WORD_LENGTH + " 个字");
        }
        return word;
    }

    private void ensureWordAbsent(String word, Long excludeId) {
        Long count = this.lambdaQuery()
                .eq(SensitiveWord::getWord, word)
                .ne(excludeId != null, SensitiveWord::getId, excludeId)
                .count();
        if (count != null && count > 0) {
            throw new BusinessException("敏感词「" + word + "」已存在");
        }
    }

    private int validType(Integer type) {
        if (type == null) {
            return SensitiveWordType.OTHER;
        }
        if (!SensitiveWordType.isValid(type)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "类型只能是 1违禁品 / 2辱骂 / 3广告 / 4其他");
        }
        return type;
    }

    private int validLevel(Integer level) {
        if (level == null) {
            return SensitiveWordLevel.WARN;
        }
        if (!SensitiveWordLevel.isValid(level)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "等级只能是 1警告 或 2拦截");
        }
        return level;
    }

    private SensitiveWordVO toVO(SensitiveWord entity) {
        SensitiveWordVO vo = new SensitiveWordVO();
        vo.setId(entity.getId());
        vo.setWord(entity.getWord());
        vo.setType(entity.getType());
        vo.setTypeLabel(SensitiveWordType.label(entity.getType()));
        vo.setLevel(entity.getLevel());
        vo.setLevelLabel(SensitiveWordLevel.label(entity.getLevel()));
        vo.setStatus(entity.getStatus());
        vo.setStatusLabel(Integer.valueOf(1).equals(entity.getStatus()) ? "启用" : "禁用");
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }

    /**
     * 一条词的内存表示。normalized 是归一化后的形式，匹配时用它；
     * word 是原文，命中后回显给用户看的必须是原文，
     * 否则提示里出现一串被去过空格的小写字符会让人一头雾水。
     */
    private static final class WordEntry {
        private final String word;
        private final String normalized;
        private final int level;

        private WordEntry(String word, String normalized, int level) {
            this.word = word;
            this.normalized = normalized;
            this.level = level;
        }
    }

    /**
     * 不可变词表快照。刷新即整体替换，读线程无需加锁。
     */
    private static final class WordSnapshot {

        private static final WordSnapshot EMPTY = new WordSnapshot(Collections.emptyList());

        private final List<WordEntry> entries;

        private WordSnapshot(List<WordEntry> entries) {
            this.entries = Collections.unmodifiableList(entries);
        }

        private boolean isEmpty() {
            return entries.isEmpty();
        }

        /**
         * 把命中的词按等级分别塞进两个集合。
         * <p>
         * 用 {@code String.contains} 而不是正则：中文词逐字比对即可，
         * 正则要为每个词编译一次 Pattern，1000 个词就是 1000 次编译，
         * 放在消息发送路径上是自找麻烦。词表上万时应换 Aho-Corasick 自动机，
         * 那是「一次扫描找出全部模式」的经典算法，本系统词表规模用不上。
         */
        private void match(String normalizedText, Set<String> blockSink, Set<String> warnSink) {
            for (WordEntry entry : entries) {
                if (entry.level == SensitiveWordLevel.BLOCK) {
                    // 已经被拦截级命中过的词不必再判断，直接跳过
                    if (normalizedText.contains(entry.normalized)
                            && !blockSink.contains(entry.word) && !warnSink.contains(entry.word)) {
                        blockSink.add(entry.word);
                    }
                } else if (normalizedText.contains(entry.normalized) && !blockSink.contains(entry.word)) {
                    warnSink.add(entry.word);
                }
            }
        }
    }
}
