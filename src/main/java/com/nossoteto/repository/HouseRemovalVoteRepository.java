package com.nossoteto.repository;

import com.nossoteto.model.HouseRemovalVote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HouseRemovalVoteRepository extends JpaRepository<HouseRemovalVote, String> {

    Optional<HouseRemovalVote> findByRemovalIdAndVoterId(String removalId, String voterId);

    List<HouseRemovalVote> findByRemovalId(String removalId);
}
