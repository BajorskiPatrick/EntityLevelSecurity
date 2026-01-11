package com.els.demo.dto;

import com.els.domain.AccessType;
import com.els.domain.Action;
import lombok.Data;

@Data
public class PermissionRequest {
    private String username; // optional (if for user)
    private Long roleId; // optional (if for role)
    private String entityName;
    private Action action;
    private AccessType accessType;
    private String rowIds;
}
