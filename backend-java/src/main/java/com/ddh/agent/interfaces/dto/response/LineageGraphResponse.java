package com.ddh.agent.interfaces.dto.response;

import lombok.Data;

import java.util.List;

@Data
public class LineageGraphResponse {
    private List<GraphNodeResponse> nodes;
    private List<GraphEdgeResponse> edges;
}
