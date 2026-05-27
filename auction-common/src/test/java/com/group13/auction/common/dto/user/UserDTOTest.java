package com.group13.auction.common.dto.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("UserDTO — unit")
class UserDTOTest {

  @Nested
  @DisplayName("Default constructor")
  class DefaultConstructorTest {
    @Test
    @DisplayName("tất cả field null / zero / false")
    void defaults() {
      UserDTO dto = new UserDTO();
      assertThat(dto.getId()).isNull();
      assertThat(dto.getUsername()).isNull();
      assertThat(dto.getEmail()).isNull();
      assertThat(dto.getRoles()).isNull();
      assertThat(dto.getAccountStatus()).isNull();
      assertThat(dto.getRating()).isZero();
      assertThat(dto.getBalance()).isZero();
      assertThat(dto.getLockedDeposit()).isZero();
      assertThat(dto.getAvailableBalance()).isZero();
      assertThat(dto.isHasEverBeenPenalized()).isFalse();
      assertThat(dto.getCreatedAt()).isNull();
      assertThat(dto.getUpdatedAt()).isNull();
      assertThat(dto.getAdminType()).isNull();
    }
  }

  @Nested
  @DisplayName("Setters / Getters — roundtrip")
  class SettersGettersTest {

    @Test
    @DisplayName("id, username, email, accountStatus")
    void identityFields() {
      UserDTO dto = new UserDTO();
      dto.setId("u-1");
      dto.setUsername("alice");
      dto.setEmail("alice@test.com");
      dto.setAccountStatus("ACTIVE");
      assertThat(dto.getId()).isEqualTo("u-1");
      assertThat(dto.getUsername()).isEqualTo("alice");
      assertThat(dto.getEmail()).isEqualTo("alice@test.com");
      assertThat(dto.getAccountStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("roles — list preserved")
    void roles_listPreserved() {
      UserDTO dto = new UserDTO();
      List<String> roles = Arrays.asList("BIDDER", "SELLER");
      dto.setRoles(roles);
      assertThat(dto.getRoles()).containsExactly("BIDDER", "SELLER");
    }

    @Test
    @DisplayName("financial fields — balance, lockedDeposit, availableBalance")
    void financialFields() {
      UserDTO dto = new UserDTO();
      dto.setBalance(10_000_000L);
      dto.setLockedDeposit(500_000L);
      dto.setAvailableBalance(9_500_000L);
      assertThat(dto.getBalance()).isEqualTo(10_000_000L);
      assertThat(dto.getLockedDeposit()).isEqualTo(500_000L);
      assertThat(dto.getAvailableBalance()).isEqualTo(9_500_000L);
    }

    @Test
    @DisplayName("rating — double roundtrip")
    void rating_roundtrip() {
      UserDTO dto = new UserDTO();
      dto.setRating(4.5);
      assertThat(dto.getRating()).isEqualTo(4.5);
    }

    @Test
    @DisplayName("hasEverBeenPenalized — set true / false")
    void penalized_toggle() {
      UserDTO dto = new UserDTO();
      dto.setHasEverBeenPenalized(true);
      assertThat(dto.isHasEverBeenPenalized()).isTrue();
      dto.setHasEverBeenPenalized(false);
      assertThat(dto.isHasEverBeenPenalized()).isFalse();
    }

    @Test
    @DisplayName("timestamps — createdAt, updatedAt")
    void timestamps() {
      UserDTO dto = new UserDTO();
      LocalDateTime ts = LocalDateTime.of(2025, 1, 15, 10, 30);
      dto.setCreatedAt(ts);
      dto.setUpdatedAt(ts.plusDays(1));
      assertThat(dto.getCreatedAt()).isEqualTo(ts);
      assertThat(dto.getUpdatedAt()).isEqualTo(ts.plusDays(1));
    }

    @Test
    @DisplayName("adminType — null cho NormalUser, MASTER/STAFF cho Admin")
    void adminType_values() {
      UserDTO dto = new UserDTO();
      assertThat(dto.getAdminType()).isNull();
      dto.setAdminType("STAFF");
      assertThat(dto.getAdminType()).isEqualTo("STAFF");
      dto.setAdminType("MASTER");
      assertThat(dto.getAdminType()).isEqualTo("MASTER");
      dto.setAdminType(null);
      assertThat(dto.getAdminType()).isNull();
    }
  }

  @Nested
  @DisplayName("AccountStatus values")
  class AccountStatusTest {
    @Test
    @DisplayName("các giá trị hợp lệ theo contract")
    void validAccountStatuses() {
      for (String status : new String[] {"ACTIVE", "SUSPENDED", "BANNED", "DELETED"}) {
        UserDTO dto = new UserDTO();
        dto.setAccountStatus(status);
        assertThat(dto.getAccountStatus()).isEqualTo(status);
      }
    }
  }
}
