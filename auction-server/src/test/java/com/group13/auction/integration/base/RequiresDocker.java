package com.group13.auction.integration.base;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Đánh dấu test class cần Docker daemon (Testcontainers). Không có condition check — Docker phải
 * đang chạy khi chạy test.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresDocker {}
