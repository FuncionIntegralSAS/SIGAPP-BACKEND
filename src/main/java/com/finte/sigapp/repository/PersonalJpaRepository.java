package com.finte.sigapp.repository;

import com.finte.sigapp.entity.PersonalEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PersonalJpaRepository extends JpaRepository<PersonalEntity,String> {

    Optional<PersonalEntity> findByPersiden(String documento);

}
