package com.genersoft.iot.vmp.vmanager.bean;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.ArrayList;
import java.util.List;

/**
 * 国标服务配置保存结果，区分已即时生效的身份字段与仍需重启的绑定字段。
 */
@Schema(description = "国标服务配置保存结果")
public class SipServerSaveResult {

    @Schema(description = "被修改的配置文件路径")
    private String filePath;

    @Schema(description = "已立即写入运行时的身份字段")
    private List<String> appliedFields = new ArrayList<>();

    @Schema(description = "本次变更但需重启的绑定字段")
    private List<String> restartRequiredFields = new ArrayList<>();

    @Schema(description = "是否需要重启服务才能生效")
    private boolean restartRequired;

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public List<String> getAppliedFields() {
        return appliedFields;
    }

    public void setAppliedFields(List<String> appliedFields) {
        this.appliedFields = appliedFields;
    }

    public List<String> getRestartRequiredFields() {
        return restartRequiredFields;
    }

    public void setRestartRequiredFields(List<String> restartRequiredFields) {
        this.restartRequiredFields = restartRequiredFields;
    }

    public boolean isRestartRequired() {
        return restartRequired;
    }

    public void setRestartRequired(boolean restartRequired) {
        this.restartRequired = restartRequired;
    }
}
