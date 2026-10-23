package com.hengshucredit.rule.model.dto;

import java.util.List;
import java.util.Map;

public record FunctionSignatureImpact(String beforeSignature, String afterSignature,
                                      List<String> changes, List<Map<String, Object>> consumers,
                                      String message) { }
