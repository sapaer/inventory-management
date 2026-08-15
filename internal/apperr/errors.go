package apperr

import "fmt"

type AppError struct {
	Code       string
	Message    string
	HTTPStatus int
}

func (e *AppError) Error() string {
	return fmt.Sprintf("%s: %s", e.Code, e.Message)
}

func NotFound(message string) *AppError {
	return &AppError{Code: "NOT_FOUND", Message: message, HTTPStatus: 404}
}

func BadRequest(code, message string) *AppError {
	return &AppError{Code: code, Message: message, HTTPStatus: 400}
}

func Unauthorized(message string) *AppError {
	return &AppError{Code: "UNAUTHORIZED", Message: message, HTTPStatus: 401}
}

func Forbidden(message string) *AppError {
	return &AppError{Code: "FORBIDDEN", Message: message, HTTPStatus: 403}
}

func Conflict(code, message string) *AppError {
	return &AppError{Code: code, Message: message, HTTPStatus: 409}
}

func TooManyRequests(code, message string) *AppError {
	return &AppError{Code: code, Message: message, HTTPStatus: 429}
}

func AsAppError(err error) (*AppError, bool) {
	if err == nil {
		return nil, false
	}
	ae, ok := err.(*AppError)
	return ae, ok
}
