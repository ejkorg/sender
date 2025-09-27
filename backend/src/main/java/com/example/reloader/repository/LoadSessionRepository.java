package com.example.reloader.repository;

import com.example.reloader.entity.LoadSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LoadSessionRepository extends JpaRepository<LoadSession, Long> {
}
