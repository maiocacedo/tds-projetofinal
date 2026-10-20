package com.nossoteto.repository;

import com.nossoteto.model.MatchVote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MatchVoteRepository extends JpaRepository<MatchVote, String> {
    Optional<MatchVote> findByRequestIdAndVoterId(String requestId, String voterId);
    List<MatchVote> findByRequestId(String requestId);
}
