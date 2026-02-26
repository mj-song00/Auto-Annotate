package auto.annotate.domain.user.service;

import auto.annotate.common.config.PasswordEncoder;
import auto.annotate.common.exception.BaseException;
import auto.annotate.common.exception.ExceptionEnum;
import auto.annotate.domain.user.dto.AuthUser;
import auto.annotate.domain.user.dto.request.SignupRequest;
import auto.annotate.domain.user.dto.response.UserProfileResponse;
import auto.annotate.domain.user.entity.User;
import auto.annotate.domain.user.enums.UserRole;
import auto.annotate.domain.user.reposotiry.UserRepository;
import auto.annotate.domain.user.validation.UserValidation;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService{
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserValidation userValidation;
    private final AuthService authService;

    @Transactional
    @Override
    public void createUser(SignupRequest signupRequest){
        Optional<User> userByEmail = userRepository.findByEmail(signupRequest.getEmail());
        Optional<User> userByNickname = userRepository. findByNickName(signupRequest.getNickName());

        if (userByEmail.isPresent()) {
            throw new BaseException(ExceptionEnum.USER_ALREADY_EXISTS);
        }

        if (userByNickname.isPresent()) {
            throw new BaseException(ExceptionEnum.USER_ALREADY_EXISTS);
        }

        String encodedPassword = passwordEncoder.encode(signupRequest.getPassword());

        UserRole userRole = UserRole.USER;


        User user = new User(
                signupRequest.getEmail(),
                signupRequest.getNickName(),
                encodedPassword,
                userRole
        );

        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    @Override
    public UserProfileResponse getUserProfile(AuthUser authUser) {
        // 인증된 사용자 확인
        userValidation.validateAuthenticatedUser(authUser);

        // 인증된 사용자의 ID로 사용자 조회
        User user = userValidation.findUserById(authUser.getId());
        // 사용자 탈퇴 여부 확인
        userValidation.validateUserNotDeleted(user);

        // 사용자 정보 반환
        return UserProfileResponse.of(user);
    }

    // 비밀번호 변경
    @Transactional
    @Override
    public void changePassword(AuthUser authUser, String oldPassword, String newPassword) {

        // 인증된 사용자 확인
        userValidation.validateAuthenticatedUser(authUser);

        // 인증된 사용자의 ID로 사용자 조회
        User user = userValidation.findUserById(authUser.getId());

        // 사용자 탈퇴 여부 확인
        userValidation.validateUserNotDeleted(user);

        // 현재 비밀번호 확인
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new BaseException(ExceptionEnum.PASSWORD_MISMATCH);
        }

        // 새 비밀번호가 기존 비밀번호와 동일한지 확인
        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new BaseException(ExceptionEnum.PASSWORD_SAME_AS_OLD);
        }

        // 새 비밀번호로 업데이트
        user.updatePassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    @Transactional
    @Override
    public void changeNickName(AuthUser authenticatedUser, String newNickName) {

        // 인증된 사용자 확인
        userValidation.validateAuthenticatedUser(authenticatedUser);

        // 인증된 사용자의 ID로 사용자 조회
        User user = userValidation.findUserById(authenticatedUser.getId());

        // 사용자 탈퇴 여부 확인
        userValidation.validateUserNotDeleted(user);

        // 새로운 닉네임이 기존 닉네임과 동일한지 확인
        if (user.getNickName().equals(newNickName)) {
            throw new BaseException(ExceptionEnum.NICKNAME_SAME_AS_OLD);
        }

        // 닉네임 업데이트
        user.updateNickName(newNickName);
        userRepository.save(user);
    }

    // 회원탈퇴
    @Transactional
    @Override
    public void deleteUser(AuthUser authenticatedUser, String refreshToken, HttpServletResponse response) {

        // 인증된 사용자 확인
        userValidation.validateAuthenticatedUser(authenticatedUser);

        // 인증된 사용자의 ID로 사용자 조회
        User user = userValidation.findUserById(authenticatedUser.getId());

        // 사용자 탈퇴 여부 확인
        userValidation.validateUserNotDeleted(user);

        // 사용자 소프트 삭제 처리
        user.updateDeletedAt();
        userRepository.save(user);

        // 로그아웃 처리 (리프레시 토큰 및 쿠키 삭제)
        authService.logout(refreshToken, response);

    }

    public User findByIdOrThrow(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new BaseException(ExceptionEnum.USER_NOT_FOUND));
    }
}
