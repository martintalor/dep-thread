package com.iflytek.dep.server.model;

/**
 * @author yftao
 * @version V1.0
 * @Package com.iflytek.dep.server.model
 * @Description: UnfinishedPack 未完成数据包信息
 * @date 2019/3/22--15:24
 */
public class UnfinishedPack {

    private String packageId;

    private String toServerNodeId;

    public String getPackageId() {
        return packageId;
    }

    public void setPackageId(String packageId) {
        this.packageId = packageId;
    }

    public String getToServerNodeId() {
        return toServerNodeId;
    }

    public void setToServerNodeId(String toServerNodeId) {
        this.toServerNodeId = toServerNodeId;
    }
}