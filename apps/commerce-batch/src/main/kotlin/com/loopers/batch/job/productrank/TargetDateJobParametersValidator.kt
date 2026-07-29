package com.loopers.batch.job.productrank

import org.springframework.batch.core.JobParameters
import org.springframework.batch.core.JobParametersInvalidException
import org.springframework.batch.core.JobParametersValidator
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * targetDate(yyyy-MM-dd) 파라미터를 잡 기동 시점에 검증한다 — 스텝 실행 도중이 아니라 실행 전에 거부한다.
 */
class TargetDateJobParametersValidator : JobParametersValidator {
    override fun validate(parameters: JobParameters?) {
        val targetDate = parameters?.getString("targetDate")
            ?: throw JobParametersInvalidException("targetDate 잡 파라미터가 필요하다 (yyyy-MM-dd)")
        try {
            LocalDate.parse(targetDate)
        } catch (e: DateTimeParseException) {
            throw JobParametersInvalidException("targetDate 형식은 yyyy-MM-dd 여야 한다: $targetDate")
        }
    }
}
