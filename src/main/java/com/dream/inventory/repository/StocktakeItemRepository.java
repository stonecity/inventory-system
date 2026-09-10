package com.dream.inventory.repository;

import com.dream.inventory.entity.StocktakeItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StocktakeItemRepository extends JpaRepository<StocktakeItem, Long> {

    List<StocktakeItem> findByStocktakeIdOrderBySkuIdAsc(Long stocktakeId);
}
