package com.aiinterviewer.dto.resp;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserResp {
    private Long id;
    private String username;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
}
