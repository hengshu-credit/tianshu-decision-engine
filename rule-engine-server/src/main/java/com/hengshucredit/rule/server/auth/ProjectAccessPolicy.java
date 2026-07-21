package com.hengshucredit.rule.server.auth;

import com.alibaba.fastjson.JSON;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** 单个 authId 的入口白名单和业务执行保护策略。 */
public class ProjectAccessPolicy {
    private static final Pattern HOST_LABEL = Pattern.compile(
            "^[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?$");
    private List<String> ipWhitelist = new ArrayList<>();
    private List<String> hostWhitelist = new ArrayList<>();
    private int qps;
    private int burst;
    private int maxConcurrent;
    private int requestTimeoutMs;

    public static ProjectAccessPolicy parse(String json) {
        ProjectAccessPolicy policy = json == null || json.trim().isEmpty()
                ? new ProjectAccessPolicy() : JSON.parseObject(json, ProjectAccessPolicy.class);
        if (policy == null) policy = new ProjectAccessPolicy();
        policy.validate();
        return policy;
    }

    public String toJson() {
        validate();
        return JSON.toJSONString(this);
    }

    public void validate() {
        if (ipWhitelist == null) ipWhitelist = new ArrayList<>();
        if (hostWhitelist == null) hostWhitelist = new ArrayList<>();
        if (ipWhitelist.size() > 256) throw new IllegalArgumentException("IP 白名单最多 256 项");
        if (hostWhitelist.size() > 256) throw new IllegalArgumentException("Host 白名单最多 256 项");
        for (String cidr : ipWhitelist) {
            if (!TrustedClientAddressResolver.isValidCidr(cidr)) {
                throw new IllegalArgumentException("IP 白名单不是合法 IP/CIDR: " + cidr);
            }
        }
        for (String host : hostWhitelist) validateHost(host);
        if (qps < 0 || qps > 100000) throw new IllegalArgumentException("qps 必须在 0 到 100000 之间");
        if (burst < 0 || burst > 1000000) throw new IllegalArgumentException("burst 必须在 0 到 1000000 之间");
        if (qps > 0 && burst == 0) burst = qps;
        if (qps > 0 && burst < qps) throw new IllegalArgumentException("burst 不能小于 qps");
        if (maxConcurrent < 0 || maxConcurrent > 10000) {
            throw new IllegalArgumentException("maxConcurrent 必须在 0 到 10000 之间");
        }
        if (requestTimeoutMs != 0 && (requestTimeoutMs < 100 || requestTimeoutMs > 600000)) {
            throw new IllegalArgumentException("requestTimeoutMs 必须为 0 或 100 到 600000 毫秒");
        }
    }

    private void validateHost(String host) {
        if (host == null || host.trim().isEmpty()) throw new IllegalArgumentException("Host 白名单不能为空");
        String value = host.trim();
        if (value.contains("://") || value.contains("/") || value.contains(":")
                || (value.contains("*") && !value.startsWith("*."))
                || value.substring(value.startsWith("*.") ? 2 : 0).contains("*")) {
            throw new IllegalArgumentException("Host 白名单格式不合法: " + host);
        }
        String hostname = value.startsWith("*.") ? value.substring(2) : value;
        if (hostname.length() > 253) {
            throw new IllegalArgumentException("Host 白名单格式不合法: " + host);
        }
        for (String label : hostname.split("\\.", -1)) {
            if (!HOST_LABEL.matcher(label).matches()) {
                throw new IllegalArgumentException("Host 白名单格式不合法: " + host);
            }
        }
    }

    public List<String> getIpWhitelist() { return ipWhitelist; }
    public void setIpWhitelist(List<String> ipWhitelist) { this.ipWhitelist = ipWhitelist; }
    public List<String> getHostWhitelist() { return hostWhitelist; }
    public void setHostWhitelist(List<String> hostWhitelist) { this.hostWhitelist = hostWhitelist; }
    public int getQps() { return qps; }
    public void setQps(int qps) { this.qps = qps; }
    public int getBurst() { return burst; }
    public void setBurst(int burst) { this.burst = burst; }
    public int getMaxConcurrent() { return maxConcurrent; }
    public void setMaxConcurrent(int maxConcurrent) { this.maxConcurrent = maxConcurrent; }
    public int getRequestTimeoutMs() { return requestTimeoutMs; }
    public void setRequestTimeoutMs(int requestTimeoutMs) { this.requestTimeoutMs = requestTimeoutMs; }
}
