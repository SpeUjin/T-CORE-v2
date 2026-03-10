package com.tcore.tcorev2.global.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CommonResponse<T> {
    private String code;
    private String message;
    private T data;

    public static <T> CommonResponse<T> success(T data) {
        return new CommonResponse<>("SUCCESS", "요청이 성공적으로 처리되었습니다.", data);
    }

    public static <T> CommonResponse<T> success(String message, T data) {
        return new CommonResponse<>("SUCCESS", message, data);
    }

    public static CommonResponse<Void> error(String message) {
        return new CommonResponse<>("ERROR", message, null);
    }
}
