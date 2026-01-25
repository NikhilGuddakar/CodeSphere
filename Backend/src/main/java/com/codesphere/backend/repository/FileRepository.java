package com.codesphere.backend.repository;

import com.codesphere.backend.entity.FileEntity;
import com.codesphere.backend.entity.ProjectEntity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FileRepository extends JpaRepository<FileEntity, Long> {

    boolean existsByFilenameAndProjectId(String filename, Long projectId);

    List<FileEntity> findByProjectId(Long projectId);

    List<FileEntity> findByProject(ProjectEntity project);

}
