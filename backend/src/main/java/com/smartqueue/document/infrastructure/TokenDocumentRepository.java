package com.smartqueue.document.infrastructure;

import com.smartqueue.document.domain.DocumentVerificationStatus;
import com.smartqueue.document.domain.TokenDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface TokenDocumentRepository extends JpaRepository<TokenDocument, UUID> {

    List<TokenDocument> findByQueueTokenIdOrderByCreatedAtDesc(UUID tokenId);

    List<TokenDocument> findByQueueTokenId(UUID tokenId);

    long countByQueueTokenIdAndVerificationStatusIn(UUID tokenId, Collection<DocumentVerificationStatus> statuses);
}
