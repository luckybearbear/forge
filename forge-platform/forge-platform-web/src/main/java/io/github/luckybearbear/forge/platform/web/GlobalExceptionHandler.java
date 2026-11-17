package io.github.luckybearbear.forge.platform.web;

import io.github.luckybearbear.forge.platform.audit.ErrorLogRecorded;
import io.github.luckybearbear.forge.platform.core.BusinessException;
import io.github.luckybearbear.forge.platform.core.ErrorCodes;
import io.github.luckybearbear.forge.platform.core.Result;
import io.github.luckybearbear.forge.platform.security.CurrentUser;
import io.github.luckybearbear.forge.platform.security.TenantContextHolder;
import io.github.luckybearbear.forge.platform.security.ThreadLocalUserProvider;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ValidationException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 全局异常处理。HTTP 状态保留语义（默认按异常类别 4xx/5xx；BusinessException 默认 200 可携带覆盖），
 * 业务语义在 Result.code。@Order(0) 抢占三方 advice。
 *
 * <p><b>错误侧审计（spec §6.6，2b Task 7 兑现）</b>：仅 500 兜底分支（{@code handleUnknown}）
 * 发布 {@link ErrorLogRecorded}——4xx 家族与业务分支<b>不发</b>：业务异常（BusinessException）
 * 是常态语义流量，记入错误表纯属噪音；4xx 是调用方错误而非服务缺陷；两者的可观测性已由
 * api_access_log.result_code 覆盖。发布经可选注入的 {@link ApplicationEventPublisher}：
 * 无发布者（standalone 单测装配 {@code new GlobalExceptionHandler()}）时静默跳过；发布失败
 * （审计不可用）仅 warn 不影响响应语义——审计绝不反噬请求处理。
 *
 * <p><b>发布事务</b>：异常处理器内通常无活跃事务，而消费方 ErrorLogListener 是
 * AFTER_COMMIT 投递（无事务发布直接丢弃）——publishEvent 以 TransactionTemplate 包裹
 * （事务管理器可选注入：standalone 装配无管理器时直接发布，无 AFTER_COMMIT 监听器场景无害）。
 */
@RestControllerAdvice
@Order(0)
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 堆栈摘要截断上限（dispatch 裁定：printStackTrace 前 2000 字符）。 */
    private static final int STACK_DIGEST_LIMIT = 2000;

    private final ApplicationEventPublisher eventPublisher;
    private final PlatformTransactionManager transactionManager;

    /** standalone 装配（MockMvc 单测/无容器场景）：无发布者，兜底分支只打日志不发事件。 */
    public GlobalExceptionHandler() {
        this(null, null);
    }

    /** starter 装配（ForgeWebAutoConfiguration）：注入容器发布器与事务管理器，兜底分支发布 ErrorLogRecorded。 */
    public GlobalExceptionHandler(ApplicationEventPublisher eventPublisher,
            PlatformTransactionManager transactionManager) {
        this.eventPublisher = eventPublisher;
        this.transactionManager = transactionManager;
    }

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

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<Result<Void>> handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        log.warn("optimistic lock conflict: {}", ex.getMessage());
        return ResponseEntity.status(409).body(Result.error(ErrorCodes.CONCURRENT_LOCKED));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleUnknown(Exception ex) {
        log.error("unhandled exception", ex);
        publishErrorLog(ex);
        return ResponseEntity.status(500).body(Result.error(ErrorCodes.INTERNAL_SERVER_ERROR));
    }

    /**
     * 方法安全拒绝（@PreAuthorize 越权）<b>必须</b>穿透本 advice：若被下面的
     * {@code @ExceptionHandler(Exception.class)} 兜底接住会误报 500/FORGE-0500。此处原样重抛——
     * Spring MVC 对 "@ExceptionHandler 方法自身抛出" 的处理是放弃解析并把原异常继续上抛，交还
     * security 链的 {@code ExceptionTranslationFilter}：已认证越权 → accessDeniedHandler →
     * 403 FORGE-0403 统一体（Spring Security 官方推荐模式，demo Task 8 集成实证：无本分支时
     * mold-viewer 越权建模具得到 500）。匿名场景本分支不可达（进不了 handler）。
     */
    @ExceptionHandler(AccessDeniedException.class)
    public void handleAccessDenied(AccessDeniedException ex) throws AccessDeniedException {
        throw ex;
    }

    /**
     * 错误侧审计发布（取舍与事务语义见类 javadoc）：请求线程此刻租户/用户上下文仍有效
     * （TokenAuthFilter 的 finally 清理在 filter 链外层回退后才执行），发布时刻快照入
     * payload——异步监听线程不继承 TTL 上下文。
     */
    private void publishErrorLog(Exception ex) {
        if (eventPublisher == null) {
            return; // standalone 装配无发布者：跳过（不炸，见构造器 javadoc）
        }
        try {
            String tenantId = TenantContextHolder.getTenantId().orElse(null);
            // ThreadLocalUserProvider 无静态读出口：无状态实例读同一 TTL 持有器（TokenAuthFilter 写入侧）
            String userId = new ThreadLocalUserProvider().currentUser().map(CurrentUser::userId).orElse(null);
            ErrorLogRecorded event = new ErrorLogRecorded(tenantId, userId,
                    ex.getClass().getName(), ex.getMessage(), stackDigestOf(ex), Instant.now());
            if (transactionManager != null) {
                new TransactionTemplate(transactionManager)
                        .executeWithoutResult(status -> eventPublisher.publishEvent(event));
            } else {
                eventPublisher.publishEvent(event); // 无事务基建（standalone）：直接发布
            }
        } catch (Exception publishEx) {
            log.warn("error log publish skipped: {}", publishEx.getMessage(), publishEx);
        }
    }

    private static String stackDigestOf(Exception ex) {
        StringWriter stringWriter = new StringWriter();
        ex.printStackTrace(new PrintWriter(stringWriter));
        String stack = stringWriter.toString();
        return stack.length() > STACK_DIGEST_LIMIT ? stack.substring(0, STACK_DIGEST_LIMIT) : stack;
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
