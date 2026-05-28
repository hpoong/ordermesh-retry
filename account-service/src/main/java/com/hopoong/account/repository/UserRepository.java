package com.hopoong.account.repository;

import com.hopoong.account.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<UserEntity, Long>, UserQueryDslRepository {

    boolean existsByLoginIdAndDeletedAtIsNull(String loginId);

    boolean existsByEmailAndDeletedAtIsNull(String email);

    boolean existsByLoginIdAndIdNotAndDeletedAtIsNull(String loginId, Long id);

    boolean existsByEmailAndIdNotAndDeletedAtIsNull(String email, Long id);
}
