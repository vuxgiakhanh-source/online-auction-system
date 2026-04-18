package com.group13.auction.model.user;

import com.group13.auction.dao.UserDAO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class UserFactoryTest {

    private static final class FakeUserDAO extends UserDAO {
        private boolean usernameExists;
        private boolean emailExists;

        FakeUserDAO(boolean usernameExists, boolean emailExists) {
            this.usernameExists = usernameExists;
            this.emailExists = emailExists;
        }

        @Override
        public boolean existsByUsername(String username) {
            return usernameExists;
        }

        @Override
        public boolean existsByEmail(String email) {
            return emailExists;
        }
    }

    @Test
    void createUser_rejectsShortUsername() {
        NormalUserFactory factory = new NormalUserFactory();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> factory.createUser("abc", "password1", "a@b.com"));
        assertEquals("Username phải từ 8 ký tự trở lên.", ex.getMessage());
    }

    @Test
    void createUser_rejectsShortPassword() {
        NormalUserFactory factory = new NormalUserFactory();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> factory.createUser("username", "short", "a@b.com"));
        assertEquals("Password phải từ 8 ký tự trở lên.", ex.getMessage());
    }

    @Test
    void createUser_rejectsInvalidEmail() {
        NormalUserFactory factory = new NormalUserFactory();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> factory.createUser("username", "password1", "not-an-email"));
        assertEquals("Email không đúng định dạng.", ex.getMessage());
    }

    @Test
    void createUser_rejectsDuplicateUsernameViaDao() {
        UserDAO userDAO = new FakeUserDAO(true, false);
        NormalUserFactory factory = new NormalUserFactory();
        factory.setUserDAO(userDAO);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> factory.createUser("username", "password1", "a@b.com"));
        assertEquals("Thông tin đăng ký không hợp lệ.", ex.getMessage());
    }

    @Test
    void createUser_rejectsDuplicateEmailViaDao() {
        UserDAO userDAO = new FakeUserDAO(false, true);
        NormalUserFactory factory = new NormalUserFactory();
        factory.setUserDAO(userDAO);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> factory.createUser("username", "password1", "a@b.com"));
        assertEquals("Email đã được sử dụng.", ex.getMessage());
    }

    @Test
    void isEmailAlreadyUsed_delegatesToDaoWhenPresent() {
        UserDAO userDAO = new FakeUserDAO(false, true);
        NormalUserFactory factory = new NormalUserFactory();
        factory.setUserDAO(userDAO);

        org.junit.jupiter.api.Assertions.assertTrue(factory.isEmailAlreadyUsed("a@b.com"));
    }
}

