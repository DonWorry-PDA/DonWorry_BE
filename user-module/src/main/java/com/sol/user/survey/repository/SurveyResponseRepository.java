package com.sol.user.survey.repository;

import com.sol.user.survey.entity.SurveyResponse;
import com.sol.user.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SurveyResponseRepository extends JpaRepository<SurveyResponse, Long> {

    List<SurveyResponse> findByUser(User user);

    List<SurveyResponse> findByUserAndQuestionCodeIn(User user, List<String> questionCodes);

    @Modifying
    @Query("DELETE FROM SurveyResponse s WHERE s.user = :user")
    void deleteAllByUser(@Param("user") User user);
}
