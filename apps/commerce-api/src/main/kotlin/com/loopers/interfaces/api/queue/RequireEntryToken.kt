package com.loopers.interfaces.api.queue

/**
 * 이 애너테이션이 붙은 핸들러는 유효한 입장 토큰(X-Entry-Token)을 요구한다.
 * EntryTokenInterceptor 가 preHandle 에서 검증한다.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class RequireEntryToken
