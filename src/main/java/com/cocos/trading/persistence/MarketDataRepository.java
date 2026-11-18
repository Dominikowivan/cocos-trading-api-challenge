package com.cocos.trading.persistence;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.cocos.trading.persistence.entity.MarketDataEntity;

public interface MarketDataRepository extends JpaRepository<MarketDataEntity, Integer> {

	default List<LatestMarketData> findLatestMarketData(Collection<Integer> instrumentIds) {
		if (instrumentIds.isEmpty()) {
			return List.of();
		}
		return findLatestMarketDataOf(instrumentIds);
	}

	@Query(value = """
			SELECT i.id AS "instrumentId", latest.close AS "close", latest.previousclose AS "previousClose"
			FROM instruments i
			CROSS JOIN LATERAL (
			    SELECT m.close, m.previousclose
			    FROM marketdata m
			    WHERE m.instrumentid = i.id
			    ORDER BY m.date DESC
			    LIMIT 1
			) latest
			WHERE i.id IN (:instrumentIds)
			""", nativeQuery = true)
	List<LatestMarketData> findLatestMarketDataOf(@Param("instrumentIds") Collection<Integer> instrumentIds);

}
