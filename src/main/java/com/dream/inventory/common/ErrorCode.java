package com.dream.inventory.common;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    OK(0, "success", HttpStatus.OK),
    VALIDATION_ERROR(40000, "参数校验失败", HttpStatus.BAD_REQUEST),
    UNAUTHORIZED(40100, "未登录或 Token 失效", HttpStatus.UNAUTHORIZED),
    FORBIDDEN(40300, "无权限", HttpStatus.FORBIDDEN),
    NOT_FOUND(40400, "资源不存在", HttpStatus.NOT_FOUND),
    STATE_CONFLICT(40900, "状态冲突", HttpStatus.CONFLICT),
    CONCURRENT_CONFLICT(40901, "并发冲突，请重试", HttpStatus.CONFLICT),
    INSUFFICIENT_STOCK(42201, "可用库存不足", HttpStatus.UNPROCESSABLE_ENTITY),
    INVENTORY_LOCKED(42202, "库存处于盘点锁定", HttpStatus.UNPROCESSABLE_ENTITY),
    ILLEGAL_STATE_TRANSITION(42203, "非法状态流转", HttpStatus.UNPROCESSABLE_ENTITY),
    RETURN_EXCEEDS_ORIGINAL(42204, "退货量超出原单", HttpStatus.UNPROCESSABLE_ENTITY),
    STOCKTAKE_SCOPE_OVERLAP(42205, "盘点范围重叠", HttpStatus.UNPROCESSABLE_ENTITY),
    STOCKTAKE_BLOCKED_BY_MOVEMENT(42206, "存在执行中单据阻塞盘点", HttpStatus.UNPROCESSABLE_ENTITY),
    LOSS_EXCEEDS_RESERVED(42207, "盘亏后实物少于已预留", HttpStatus.UNPROCESSABLE_ENTITY),
    INTERNAL_ERROR(50000, "服务器内部错误", HttpStatus.INTERNAL_SERVER_ERROR);

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;

    ErrorCode(int code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}
