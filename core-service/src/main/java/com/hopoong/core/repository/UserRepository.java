package com.hopoong.core.repository;

import com.hopoong.core.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<UserEntity, Long> {

    boolean existsByLoginIdAndIdNot(String loginId, Long id);

    boolean existsByEmailAndIdNot(String email, Long id);
}
