package com.sitegenius.whatsappbe.repository;

import com.sitegenius.whatsappbe.entity.FlowNode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FlowNodeRepository extends JpaRepository<FlowNode, Long> {

    List<FlowNode> findByFlowIdOrderByIdAsc(Long flowId);

    Optional<FlowNode> findByIdAndFlowId(
            Long id,
            Long flowId
    );

    Optional<FlowNode> findByFlowIdAndNodeKey(
            Long flowId,
            String nodeKey
    );

    boolean existsByFlowIdAndNodeKey(
            Long flowId,
            String nodeKey
    );

    void deleteByFlowId(Long flowId);
}