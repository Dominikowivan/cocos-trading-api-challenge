package com.cocos.trading.persistence;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.cocos.trading.persistence.entity.MarketDataEntity;

public interface MarketDataRepository extends JpaRepository<MarketDataEntity, Integer> {

	default List<LatestClose> findLatestCloses(Collection<Integer> instrumentIds) {
		if (instrumentIds.isEmpty()) {
			return List.of();
		}
		return findLatestClosesOf(instrumentIds);
	}

	@Query(value = "SELECT DISTINCT ON (m.instrumentid) m.instrumentid AS \"instrumentId\", m.close AS \"close\""
			+ " FROM marketdata m WHERE m.instrumentid IN (:instrumentIds)"
			+ " ORDER BY m.instrumentid, m.date DESC", nativeQuery = true)
	List<LatestClose> findLatestClosesOf(@Param("instrumentIds") Collection<Integer> instrumentIds);

}
