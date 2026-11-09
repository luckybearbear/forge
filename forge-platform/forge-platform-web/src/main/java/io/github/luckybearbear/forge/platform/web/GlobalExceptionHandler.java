package io.github.luckybearbear.forge.platform.web;

import io.github.luckybearbear.forge.platform.core.BusinessException;
import io.github.luckybearbear.forge.platform.core.ErrorCodes;
import io.github.luckybearbear.forge.platform.core.Result;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ValidationException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理。HTTP 状态保留语义（默认按异常类别 4xx/5xx；BusinessException 默认 200 可携带覆盖），
 * 业务语义在 Result.code。@Order(0) 抢占三方 advice。
 */
@RestControllerAdvice
@Order(0)
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusiness(BusinessException ex) {
        // 防御：业务码为成功码属于编码错误，改判 500（借鉴 yudao Issue #1196）
        if (Result.SUCCESS_CODE.equals(ex.errorCode().code())) {
            log.error("BusinessException carried success code, remapped to FORGE-0500", ex);
            return ResponseEntity.status(500).body(Result.error(ErrorCodes.INTERNAL_SERVER_ERROR));
        }
        log.warn("business exception [{}]: {}", ex.errorCode().code(), ex.getMessage());
        int status = ex.httpStatus() != null ? ex.httpStatus() : 200;
        return ResponseEntity.status(status).body(Result.error(ex.errorCode(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Map<String, String>>> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        return badRequest(fieldErrors(ex));
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<Result<Map<String, String>>> handleBind(BindException ex) {
        return badRequest(fieldErrors(ex));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Map<String, String>>> handleConstraintViolation(ConstraintViolationException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getConstraintViolations().forEach(v -> errors.put(v.getPropertyPath().toString(), v.getMessage()));
        return badRequest(errors);
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<Result<Void>> handleValidation(ValidationException ex) {
        log.warn("validation exception: {}", ex.getMessage());
        return badRequestMessage("请求参数校验失败");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Result<Void>> handleMissingParameter(MissingServletRequestParameterException ex) {
        return badRequestMessage("缺少请求参数: " + ex.getParameterName());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Result<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return badRequestMessage("参数类型不正确: " + ex.getName());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> handleNotReadable(HttpMessageNotReadableException ex) {
        log.warn("unreadable request body: {}", ex.getMessage());
        return badRequestMessage("请求体不可读或格式错误");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Result<Void>> handleMediaType(HttpMediaTypeNotSupportedException ex) {
        return ResponseEntity.status(415)
                .body(Result.error(ErrorCodes.UNSUPPORTED_MEDIA_TYPE));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Result<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return ResponseEntity.status(405)
                .body(Result.error(ErrorCodes.METHOD_NOT_ALLOWED));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Result<Void>> handleMaxUpload(MaxUploadSizeExceededException ex) {
        return badRequestMessage("上传文件超出大小限制");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result<Void>> handleNoResource(NoResourceFoundException ex) {
        return ResponseEntity.status(404).body(Result.error(ErrorCodes.NOT_FOUND, "请求路径不存在"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleUnknown(Exception ex) {
        log.error("unhandled exception", ex);
        return ResponseEntity.status(500).body(Result.error(ErrorCodes.INTERNAL_SERVER_ERROR));
    }

    private Map<String, String> fieldErrors(BindException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            errors.put(fe.getField(), fe.getDefaultMessage());
        }
        ex.getBindingResult().getGlobalErrors()
                .forEach(e -> errors.put(e.getObjectName(), e.getDefaultMessage()));
        return errors;
    }

    private ResponseEntity<Result<Map<String, String>>> badRequest(Map<String, String> errors) {
        return ResponseEntity.status(400)
                .body(Result.error(ErrorCodes.PLATFORM_BAD_REQUEST, "请求参数不正确", errors));
    }

    private ResponseEntity<Result<Void>> badRequestMessage(String message) {
        return ResponseEntity.status(400).body(Result.error(ErrorCodes.PLATFORM_BAD_REQUEST, message));
    }
}
