package com.sparta.ecommerce.domain.user.exception;

import com.sparta.ecommerce.domain.common.exception.BusinessException;
import com.sparta.ecommerce.domain.common.exception.ErrorCode;

/**
 * 사용자를 찾을 수 없을 때 발생하는 예외
 */
public class UserNotFoundException extends BusinessException {
    public UserNotFoundException() {
        super(ErrorCode.COMMON003, "사용자를 찾을 수 없습니다");
    }

    public UserNotFoundException(String userId) {
        super(ErrorCode.COMMON003, "사용자를 찾을 수 없습니다: " + userId);
    }
}
