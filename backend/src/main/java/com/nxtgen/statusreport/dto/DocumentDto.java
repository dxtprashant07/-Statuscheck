package com.nxtgen.statusreport.dto;

import java.time.Instant;

public record DocumentDto(Long id, String originalFilename, Instant uploadedAt) {
}
