package com.hengshucredit.rule.model.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class ConsoleUserSaveRequest {
    private Long id;
    private String username;
    private String displayName;
    private String email;
    private String password;
    private Integer status;
    private List<Long> roleIds;
    private Map<String, String> permissionOverrides;
}
