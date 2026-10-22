// 文件路径: src/main/java/com/campus/market/vo/FollowStateVO.java
package com.campus.market.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 关注切换结果。fansCount 为被关注者的最新粉丝数，供详情页即时刷新。
 */
@Data
@Schema(description = "关注状态")
public class FollowStateVO {

    @Schema(description = "本次操作后是否已关注")
    private Boolean followed;

    @Schema(description = "被关注者的最新粉丝数")
    private Integer fansCount;

    public static FollowStateVO of(boolean followed, Integer fansCount) {
        FollowStateVO vo = new FollowStateVO();
        vo.setFollowed(followed);
        vo.setFansCount(fansCount);
        return vo;
    }
}
