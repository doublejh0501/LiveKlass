package com.futurescholae.liveklass.settlement.repository;

import com.futurescholae.liveklass.settlement.domain.Course;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseRepository extends JpaRepository<Course, String> {
}
