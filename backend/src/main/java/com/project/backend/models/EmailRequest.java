package com.project.backend.models;

import lombok.Data;

@Data
public class EmailRequest {
    private String emailContent;
    private String tone;
}
