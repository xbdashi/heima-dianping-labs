package com.example.dianping.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Result<T> {
    private int code;
    private String message;
    private T data;

    public static <T> Result<T> success(T data) {
        return new Result<>(200, "success", data);
    }

    public static <T> Result<T> error(int code, String message) {
        return new Result<>(code, message, null);
    }

    public static<T> Result<T> error(int code){
        return new Result<>(code, "error", null);
    }
    public static<T> Result<T> success(){
        return new Result<>(200, "success", null);
    }
}
