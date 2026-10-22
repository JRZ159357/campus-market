// 文件路径: src/main/java/com/campus/market/vo/SensitiveWordBatchVO.java
package com.campus.market.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 批量添加结果。
 * <p>
 * 必须把「跳过」的明细返回给前端：管理员粘一份 200 行的词表，只看
 * 「成功 187 条」会一直怀疑那 13 条去哪了，然后反复重试。
 * 明确列出「已存在 10 条、格式非法 3 条」他一次就能改对。
 */
@Data
@Schema(description = "敏感词批量添加结果")
public class SensitiveWordBatchVO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "文本中识别到的有效行数（去空行、去重后）")
    private Integer total;

    @Schema(description = "实际新增条数")
    private Integer inserted;

    @Schema(description = "因已存在而跳过的条数")
    private Integer duplicated;

    @Schema(description = "因超长/含非法字符被跳过的条数")
    private Integer invalid;

    @Schema(description = "因已存在而跳过的词（最多回显50个）")
    private List<String> duplicatedWords;

    @Schema(description = "因格式非法而跳过的词（最多回显50个）")
    private List<String> invalidWords;
}
