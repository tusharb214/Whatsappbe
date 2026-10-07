package com.sitegenius.whatsappbe.repository;

import com.sitegenius.whatsappbe.entity.FlowEdge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FlowEdgeRepository extends JpaRepository<FlowEdge, Long> {

    List<FlowEdge> findByFlowIdOrderByIdAsc(Long flowId);

    Optional<FlowEdge> findByIdAndFlowId(
            Long id,
            Long flowId
    );

    List<FlowEdge> findBySourceNodeId(Long sourceNodeId);

    List<FlowEdge> findByTargetNodeId(Long targetNodeId);

    void deleteByFlowId(Long flowId);

    void deleteBySourceNodeId(Long sourceNodeId);

    void deleteByTargetNodeId(Long targetNodeId);
}