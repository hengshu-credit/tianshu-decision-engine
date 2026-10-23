package com.hengshucredit.rule.model.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class ConsoleUserSaveRequest {
    private java.util.List<Long> projectIds;
    private String projectAccessMode;
    private Boolean globalEditable;
    private java.util.Map<Long, java.util.Map<String, String>> projectPermissionOverrides;
    private Long id;
    private String username;
    private String displayName;
    private String email;
    private String password;
    private Integer status;
    private List<Long> roleIds;
    private Map<String, String> permissionOverrides;
}
