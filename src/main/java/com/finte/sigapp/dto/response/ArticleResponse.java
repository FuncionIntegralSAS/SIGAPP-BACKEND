package com.finte.sigapp.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ArticleResponse {
    private String id;
    private String name;
    private String licensePlate;
    private String warehouse;
    private String responsible;
    private String status;
    private Double latitude;
    private Double longitude;
    private String comments;
    private String photoPath;
}