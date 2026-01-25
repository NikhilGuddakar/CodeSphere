package com.codesphere.backend.repository;

import com.codesphere.backend.entity.ProjectEntity;
import com.codesphere.backend.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectRepository extends JpaRepository<ProjectEntity, Long> {

    boolean existsByNameAndUser(String name, UserEntity user);

    List<ProjectEntity> findByUser(UserEntity user);

    Optional<ProjectEntity> findByNameAndUser(String name, UserEntity user);
}

