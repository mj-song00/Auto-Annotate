package auto.annotate.domain.user.service;

import auto.annotate.domain.user.dto.AuthUser;
import auto.annotate.domain.user.dto.request.SignupRequest;
import auto.annotate.domain.user.dto.response.UserProfileResponse;
import jakarta.servlet.http.HttpServletResponse;

public interface UserService {
    void createUser(SignupRequest signupRequest);

    UserProfileResponse getUserProfile(AuthUser authUser);

    void changePassword(AuthUser authUser,String oldPassword, String newPassword);

    void changeNickName(AuthUser authUser,  String newNickName);

    void deleteUser(AuthUser authenticatedUser, String refreshToken, HttpServletResponse response);
}
