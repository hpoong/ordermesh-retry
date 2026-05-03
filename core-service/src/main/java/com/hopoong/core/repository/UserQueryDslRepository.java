package com.hopoong.core.repository;

import com.hopoong.core.entity.UserEntity;
import com.hopoong.core.enums.UserStatus;
import java.util.List;

public interface UserQueryDslRepository {

    List<UserEntity> findUsers(UserStatus status, String name, String sortBy, String sortDirection);
}

