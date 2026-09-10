package com.dream.inventory.dto.location;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class LocationBatchCreateResult {

    private int created;
    private int skipped;
    private List<LocationVO> locations;
}
