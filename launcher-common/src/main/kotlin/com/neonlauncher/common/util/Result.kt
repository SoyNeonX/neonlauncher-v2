package com.neonlauncher.common.util

sealed class Result<out T> {
    data class Success<out T>(val value: T) : Result<T>()
    data class Failure(val exception: Throwable) : Result<Nothing>()

    companion object {
        fun <T> success(value: T): Result<T> = Success(value)
        fun <T> failure(exception: Throwable): Result<T> = Failure(exception) as Result<T>
        fun <T> runCatching(block: () -> T): Result<T> =
            try { success(block()) } catch (e: Throwable) { failure(e) }
    }

    fun isSuccess(): Boolean = this is Success<*>
    fun isFailure(): Boolean = this is Failure

    fun getOrNull(): T? = when (this) {
        is Success -> value
        is Failure -> null
    }

    fun getOrElse(default: T): T = when (this) {
        is Success -> value
        is Failure -> default
    }

    fun getOrThrow(): T = when (this) {
        is Success -> value
        is Failure -> throw exception
    }

    fun <R> map(transform: (T) -> R): Result<R> = when (this) {
        is Success -> Success(transform(value))
        is Failure -> Failure(exception) as Result<R>
    }

    fun <R> flatMap(transform: (T) -> Result<R>): Result<R> = when (this) {
        is Success -> transform(value)
        is Failure -> Failure(exception) as Result<R>
    }

    fun onSuccess(action: (T) -> Unit): Result<T> = when (this) {
        is Success -> { action(value); this }
        is Failure -> this
    }

    fun onFailure(action: (Throwable) -> Unit): Result<T> = when (this) {
        is Success -> this
        is Failure -> { action(exception); this }
    }

    fun <R> fold(onSuccess: (T) -> R, onFailure: (Throwable) -> R): R = when (this) {
        is Success -> onSuccess(value)
        is Failure -> onFailure(exception)
    }
}