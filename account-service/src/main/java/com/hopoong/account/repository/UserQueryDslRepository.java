package com.hopoong.account.repository;

import com.hopoong.account.entity.UserEntity;
import com.hopoong.account.enums.UserStatus;
import java.util.List;

public interface UserQueryDslRepository {

    List<UserEntity> findUsers(UserStatus status, String name, String sortBy, String sortDirection);
}
