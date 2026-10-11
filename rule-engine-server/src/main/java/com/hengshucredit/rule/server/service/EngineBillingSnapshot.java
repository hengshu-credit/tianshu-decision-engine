package com.hengshucredit.rule.server.service;

import com.hengshucredit.rule.model.entity.RuleBillingRecord;

import java.time.LocalDateTime;
import java.util.List;

/** 已完成执行的计费事实；空明细表示当时无计费项，读取失败必须单独保留。 */
public record EngineBillingSnapshot(int version, LocalDateTime capturedAt,
                                    List<RuleBillingRecord> records, String captureError) {
}
