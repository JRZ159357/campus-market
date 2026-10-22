// 文件路径: src/main/java/com/campus/market/service/SensitiveWordService.java
package com.campus.market.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.campus.market.common.SensitiveCheckResult;
import com.campus.market.dto.SensitiveWordBatchDTO;
import com.campus.market.dto.SensitiveWordQueryDTO;
import com.campus.market.dto.SensitiveWordSaveDTO;
import com.campus.market.entity.SensitiveWord;
import com.campus.market.vo.SensitiveWordBatchVO;
import com.campus.market.vo.SensitiveWordVO;

import java.util.List;

/**
 * 敏感词服务。对外提供两类能力：
 * <ol>
 *   <li><b>词典维护</b>：管理端的增删改查、批量导入、启用禁用；</li>
 *   <li><b>内容检测</b>：{@link #check(String...)} 是三处业务场景（商品发布、
 *       评价提交、聊天发送）的唯一入口。</li>
 * </ol>
 * <p>
 * <b>为什么检测入口只留一个</b>：如果让每个业务 Service 自己查表、自己匹配，
 * 会出现三种不一致——商品发布只查「拦截」级、评价连「警告」级也拦、
 * 聊天页忘了归一化导致「加 微 信」能绕过。把匹配逻辑收在一个方法里，
 * 「什么算命中」就只有一处定义，改规则只改这里。
 * <p>
 * <b>性能设计</b>：全部启用的词在启动时加载进内存（不可变快照），
 * 检测过程不碰数据库、不加锁、不分配大对象。词典变更时整体替换快照
 * （写时复制），读请求永远不会被写操作阻塞。
 */
public interface SensitiveWordService extends IService<SensitiveWord> {

    /**
     * 分页查询。按类型 Tab + 关键词 + 状态组合筛选。
     */
    IPage<SensitiveWordVO> page(SensitiveWordQueryDTO query);

    /**
     * 新增。词重复时抛业务异常（提示已存在），成功后会刷新内存缓存。
     */
    Long create(SensitiveWordSaveDTO dto);

    /**
     * 编辑。允许改词本身——改词等同于「删一个加一个」，
     * 若新词与既有词冲突则拒绝。
     */
    void update(Long id, SensitiveWordSaveDTO dto);

    /**
     * 删除。物理删除：词表是配置数据，留一条 deleted=1 的残行
     * 只会让「唯一索引」在重新录入同一个词时冲突。
     */
    void delete(Long id);

    /**
     * 启用 / 禁用。禁用只是移出内存快照，行仍保留。
     */
    void updateStatus(Long id, Integer status);

    /**
     * 批量添加。按行拆分、去空行、去重、逐条校验，
     * 已存在的词跳过而不报错（一次性导入一份词典时不该被个别重复项卡住）。
     */
    SensitiveWordBatchVO batchCreate(SensitiveWordBatchDTO dto);

    /**
     * 全量启用词（原文），供管理端「词典体检」或调试用。
     */
    List<String> listEnabledWords();

    /**
     * 内容检测统一入口。传入若干段文本（如标题 + 描述），
     * 任一文本命中即计入结果；同一词在多段文本中命中只记一次。
     *
     * @param texts 待检测文本，允许 null / 空串，会被跳过
     * @return 检测结果，永不为 null（无命中时返回 {@link SensitiveCheckResult#pass()}）
     */
    SensitiveCheckResult check(String... texts);

    /**
     * 重新加载内存词典，返回加载的词条数。
     * <p>
     * 由写操作内部自动调用；单独暴露是为了支持「多实例部署时
     * 通过管理端手动刷新」以及排查「改了词没生效」这类问题。
     */
    int reload();
}
