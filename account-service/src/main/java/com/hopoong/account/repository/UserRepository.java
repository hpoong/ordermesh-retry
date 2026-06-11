package com.hopoong.account.repository;

import com.hopoong.account.entity.UserEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<UserEntity, Long>, UserQueryDslRepository {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM UserEntity u WHERE u.id = :id")
    Optional<UserEntity> findByIdForUpdate(@Param("id") Long id);

    boolean existsByLoginIdAndDeletedAtIsNull(String loginId);

    boolean existsByEmailAndDeletedAtIsNull(String email);

    boolean existsByLoginIdAndIdNotAndDeletedAtIsNull(String loginId, Long id);

    boolean existsByEmailAndIdNotAndDeletedAtIsNull(String email, Long id);
}
