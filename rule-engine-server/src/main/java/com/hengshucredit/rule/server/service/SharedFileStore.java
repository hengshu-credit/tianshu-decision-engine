package com.hengshucredit.rule.server.service;

/** 不可变文件按摘要去重，业务层在读取前验证资源归属。 */
public interface SharedFileStore {
    void put(String reference, String digest, byte[] content);
    void removePending(long assetId, String digest);
    byte[] read(String reference, String digest, long limit);
}
