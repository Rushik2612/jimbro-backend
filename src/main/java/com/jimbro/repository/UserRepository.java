package com.jimbro.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.jimbro.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {

    User findFirstByEmail(String email);

}
