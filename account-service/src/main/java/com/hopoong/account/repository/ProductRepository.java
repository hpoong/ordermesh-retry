package com.hopoong.account.repository;

import com.hopoong.account.entity.ProductEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<ProductEntity, Long> {

    boolean existsByProductCodeAndIdNot(String productCode, Long id);
}
