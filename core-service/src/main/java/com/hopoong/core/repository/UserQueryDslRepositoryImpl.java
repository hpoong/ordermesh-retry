package com.hopoong.core.repository;

import com.hopoong.core.entity.QUserEntity;
import com.hopoong.core.entity.UserEntity;
import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Predicate;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class UserQueryDslRepositoryImpl implements UserQueryDslRepository {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<UserEntity> findUsers(String status, String name, String sortBy, String sortDirection) {
        QUserEntity userEntity = QUserEntity.userEntity;

        return queryFactory
                .selectFrom(userEntity)
                .where(
                        userEntity.deletedAt.isNull(),
                        statusEq(status, userEntity),
                        nameContainsIgnoreCase(name, userEntity)
                )
                .orderBy(resolveSort(sortBy, sortDirection, userEntity))
                .fetch();
    }

    private static Predicate statusEq(String status, QUserEntity user) {
        if (status == null || status.isBlank()) {
            return null;
        }
        return user.status.eq(status.trim());
    }

    private static Predicate nameContainsIgnoreCase(String name, QUserEntity user) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return user.name.containsIgnoreCase(name.trim());
    }

    private static OrderSpecifier<?> resolveSort(String sortBy, String sortDirection, QUserEntity user) {
        String normalizedSortBy = sortBy == null ? "" : sortBy.trim().toLowerCase();
        String normalizedSortDirection = sortDirection == null ? "" : sortDirection.trim().toLowerCase();

        boolean desc = !"asc".equals(normalizedSortDirection);
        Order order = desc ? Order.DESC : Order.ASC;

        if ("name".equals(normalizedSortBy)) {
            return new OrderSpecifier<>(order, user.name);
        }

        // default: createdAt DESC
        return new OrderSpecifier<>(order, user.createdAt);
    }
}

