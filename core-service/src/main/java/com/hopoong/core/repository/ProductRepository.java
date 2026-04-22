package com.hopoong.core.repository;

import com.hopoong.core.entity.ProductEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<ProductEntity, Long> {

    boolean existsByProductCodeAndIdNot(String productCode, Long id);
}
