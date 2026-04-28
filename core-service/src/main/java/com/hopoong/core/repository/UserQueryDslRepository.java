package com.hopoong.core.repository;

import com.hopoong.core.entity.UserEntity;
import java.util.List;

public interface UserQueryDslRepository {

    List<UserEntity> findUsers(String status, String name, String sortBy, String sortDirection);
}

