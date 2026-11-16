package com.cocos.trading.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.cocos.trading.model.instrument.InstrumentRef;
import com.cocos.trading.persistence.entity.InstrumentEntity;

public interface InstrumentRepository extends JpaRepository<InstrumentEntity, Integer> {

	default Page<InstrumentEntity> searchNonCash(String text, int page, int size) {
		return search(text, InstrumentRef.CASH, PageRequest.of(page, size));
	}

	@Query("""
			SELECT i FROM InstrumentEntity i
			WHERE (locate(lower(:query), lower(i.ticker)) > 0
			    OR locate(lower(:query), lower(i.name)) > 0)
			  AND i.type <> :cashType
			ORDER BY i.ticker, i.id
			""")
	Page<InstrumentEntity> search(@Param("query") String query, @Param("cashType") String cashType, Pageable pageable);

}
