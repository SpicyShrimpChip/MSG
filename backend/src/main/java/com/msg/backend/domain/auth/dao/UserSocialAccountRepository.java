package com.msg.backend.domain.auth.dao;

import com.msg.backend.domain.auth.entity.SocialProvider;
import com.msg.backend.domain.auth.entity.UserSocialAccount;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserSocialAccountRepository extends JpaRepository<UserSocialAccount, Long> {

	Optional<UserSocialAccount> findByProviderAndProviderUserId(SocialProvider provider, String providerUserId);

	List<UserSocialAccount> findAllByUserId(Long userId);

	boolean existsByUserIdAndProvider(Long userId, SocialProvider provider);
}
